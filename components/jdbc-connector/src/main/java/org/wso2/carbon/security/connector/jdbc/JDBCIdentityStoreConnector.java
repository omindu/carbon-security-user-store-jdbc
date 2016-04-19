/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.security.connector.jdbc;

import org.wso2.carbon.datasource.core.exception.DataSourceException;
import org.wso2.carbon.security.connector.jdbc.constant.ConnectorConstants;
import org.wso2.carbon.security.connector.jdbc.constant.DatabaseColumnNames;
import org.wso2.carbon.security.connector.jdbc.util.DatabaseUtil;
import org.wso2.carbon.security.connector.jdbc.util.NamedPreparedStatement;
import org.wso2.carbon.security.connector.jdbc.util.UnitOfWork;
import org.wso2.carbon.security.usercore.bean.Group;
import org.wso2.carbon.security.usercore.bean.User;
import org.wso2.carbon.security.usercore.config.IdentityStoreConfig;
import org.wso2.carbon.security.usercore.connector.IdentityStoreConnector;
import org.wso2.carbon.security.usercore.exception.IdentityStoreException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * Identity store connector for JDBC based stores.
 */
public class JDBCIdentityStoreConnector extends JDBCStoreConnector implements IdentityStoreConnector {

    private DataSource dataSource;
    private IdentityStoreConfig identityStoreConfig;
    private String userStoreId;
    private String userStoreName;

    @Override
    public void init(IdentityStoreConfig identityStoreConfig) throws IdentityStoreException {

        Properties properties = identityStoreConfig.getStoreProperties();

        this.loadQueries((String) properties.get(ConnectorConstants.DATABASE_TYPE));
        this.userStoreId = properties.getProperty(ConnectorConstants.USERSTORE_ID);
        this.userStoreName = properties.getProperty(ConnectorConstants.USERSTORE_NAME);
        this.identityStoreConfig = identityStoreConfig;
        try {
            dataSource = DatabaseUtil.getInstance()
                    .getDataSource(properties.getProperty(ConnectorConstants.DATA_SOURCE));
        } catch (DataSourceException e) {
            throw new IdentityStoreException("Error occurred while initiating data source.", e);
        }
    }

    @Override
    public String getUserStoreName() {
        return userStoreName;
    }

    @Override
    public String getUserStoreID() {
        return userStoreId;
    }

    @Override
    public User getUser(String username) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_USER_FROM_USERNAME));
            namedPreparedStatement.setString("username", username);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (!resultSet.next()) {
                    throw new IdentityStoreException("No user for given id.");
                }

                String userId = resultSet.getString(DatabaseColumnNames.User.USER_UNIQUE_ID);
                long tenantId = resultSet.getLong(DatabaseColumnNames.User.TENANT_ID);

                return new User(username, userId, userStoreId, tenantId);
            }

        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving user from database.", e);
        }
    }

    @Override
    public User getUserFromId(String userID) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_USER_FROM_ID));
            namedPreparedStatement.setString("user_id", userID);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (!resultSet.next()) {
                    throw new IdentityStoreException("No user for given id.");
                }

                String username = resultSet.getString(DatabaseColumnNames.User.USERNAME);
                long tenantId = resultSet.getLong(DatabaseColumnNames.User.TENANT_ID);

                return new User(username, userID, userStoreId, tenantId);
            }

        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving user from database.", e);
        }
    }

    @Override
    public List<User> listUsers(String filterPattern, int offset, int length) throws IdentityStoreException {

        List<User> userList = new ArrayList<>();

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement listUsersNamedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_LIST_USERS));
            listUsersNamedPreparedStatement.setString("username", filterPattern);
            listUsersNamedPreparedStatement.setInt("length", length);
            listUsersNamedPreparedStatement.setInt("offset", offset);

            try (ResultSet resultSet = listUsersNamedPreparedStatement.getPreparedStatement().executeQuery()) {

                while (resultSet.next()) {
                    String userUniqueId = resultSet.getString(DatabaseColumnNames.User.USER_UNIQUE_ID);
                    String username = resultSet.getString(DatabaseColumnNames.User.USERNAME);
                    long tenantId = resultSet.getLong(DatabaseColumnNames.User.TENANT_ID);
                    userList.add(new User(username, userUniqueId, userStoreId, tenantId));
                }
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while listing users.", e);
        }

        return userList;
    }

    @Override
    public Map<String, String> getUserClaimValues(String userId) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_USER_ATTRIBUTES));
            namedPreparedStatement.setString("user_id", userId);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                Map<String, String> userClaims = new HashMap<>();

                while (resultSet.next()) {
                    String attrName = resultSet.getString(DatabaseColumnNames.UserAttributes.ATTR_NAME);
                    String attrValue = resultSet.getString(DatabaseColumnNames.UserAttributes.ATTR_VALUE);
                    userClaims.put(attrName, attrValue);
                }

                return userClaims;
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving user claims.", e);
        }
    }

    @Override
    public Map<String, String> getUserClaimValues(String userID, List<String> claimURIs) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_USER_ATTRIBUTES_FROM_URI));
            namedPreparedStatement.setString("user_id", userID);
            namedPreparedStatement.setString("claim_uris", claimURIs);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                Map<String, String> userClaims = new HashMap<>();

                while (resultSet.next()) {
                    String attrName = resultSet.getString(DatabaseColumnNames.UserAttributes.ATTR_NAME);
                    String attrValue = resultSet.getString(DatabaseColumnNames.UserAttributes.ATTR_VALUE);
                    userClaims.put(attrName, attrValue);
                }

                return userClaims;
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving user claims.");
        }
    }

    @Override
    public Group getGroup(String groupName) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_GROUP_FROM_NAME));
            namedPreparedStatement.setString("groupname", groupName);
            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (!resultSet.next()) {
                    throw new IdentityStoreException("No group for given name.");
                }
                String groupId = resultSet.getString(DatabaseColumnNames.Group.GROUP_UNIQUE_ID);

                return new Group(groupId, userStoreId, groupName);
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving group.", e);
        }
    }

    @Override
    public Group getGroupById(String groupId) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_GROUP_FROM_ID));
            namedPreparedStatement.setString("group_id", groupId);

            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                if (!resultSet.next()) {
                    throw new IdentityStoreException("No group for given id.");
                }
                String groupName = resultSet.getString(DatabaseColumnNames.Group.GROUP_NAME);
                return new Group(groupId, userStoreId, groupName);
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving group.", e);
        }
    }

    @Override
    public List<Group> listGroups(String filterPattern, int offset, int length) throws IdentityStoreException {

        List<Group> groups = new ArrayList<>();

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement listGroupsNamedPreparedStatement = new NamedPreparedStatement(
                    unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_LIST_GROUP));
            listGroupsNamedPreparedStatement.setString("group_name", filterPattern);
            listGroupsNamedPreparedStatement.setInt("length", length);
            listGroupsNamedPreparedStatement.setInt("offset", offset);

            try (ResultSet resultSet = listGroupsNamedPreparedStatement.getPreparedStatement().executeQuery()) {

                while (resultSet.next()) {
                    String groupUniqueId = resultSet.getString(DatabaseColumnNames.Group.GROUP_UNIQUE_ID);
                    String groupName = resultSet.getString(DatabaseColumnNames.Group.GROUP_NAME);
                    groups.add(new Group(groupUniqueId, userStoreId, groupName));
                }
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving group list.");
        }

        return groups;
    }

    @Override
    public List<Group> getGroupsOfUser(String userId) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_GROUPS_OF_USER));
            namedPreparedStatement.setString("user_id", userId);

            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                List<Group> groupList = new ArrayList<>();
                while (resultSet.next()) {
                    String groupName = resultSet.getString(DatabaseColumnNames.Group.GROUP_NAME);
                    String groupId = resultSet.getString(DatabaseColumnNames.Group.GROUP_UNIQUE_ID);
                    Group group = new Group(groupId, userStoreId, groupName);
                    groupList.add(group);
                }
                return groupList;
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving groups of user.", e);
        }
    }

    @Override
    public List<User> getUsersOfGroup(String groupId) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_GET_USERS_OF_GROUP));
            namedPreparedStatement.setString("group_id", groupId);

            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {

                List<User> userList = new ArrayList<>();
                while (resultSet.next()) {
                    String username = resultSet.getString(DatabaseColumnNames.User.USERNAME);
                    String userId = resultSet.getString(DatabaseColumnNames.User.USER_UNIQUE_ID);
                    long tenantId = resultSet.getLong(DatabaseColumnNames.User.TENANT_ID);
                    User user = new User(username, userId, userStoreId, tenantId);
                    userList.add(user);
                }
                unitOfWork.endTransaction();
                return userList;
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error occurred while retrieving users of group.", e);
        }
    }

    @Override
    public boolean isUserInGroup(String userId, String groupId) throws IdentityStoreException {

        try (UnitOfWork unitOfWork = UnitOfWork.beginTransaction(dataSource.getConnection())) {

            NamedPreparedStatement namedPreparedStatement = new NamedPreparedStatement(unitOfWork.getConnection(),
                    sqlQueries.get(ConnectorConstants.QueryTypes.SQL_QUERY_IS_USER_IN_GROUP));
            namedPreparedStatement.setString("user_id", userId);
            namedPreparedStatement.setString("group_id", groupId);

            try (ResultSet resultSet = namedPreparedStatement.getPreparedStatement().executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IdentityStoreException("Error while checking users in group", e);
        }
    }

    @Override
    public boolean isReadOnly() throws IdentityStoreException {
        return false;
    }

    @Override
    public IdentityStoreConfig getIdentityStoreConfig() {
        return identityStoreConfig;
    }
}
