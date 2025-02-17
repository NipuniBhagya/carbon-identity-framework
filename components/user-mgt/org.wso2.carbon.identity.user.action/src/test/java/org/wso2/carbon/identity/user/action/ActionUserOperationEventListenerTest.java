package org.wso2.carbon.identity.user.action;

import org.mockito.MockedStatic;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.action.execution.exception.ActionExecutionException;
import org.wso2.carbon.identity.action.execution.model.ActionExecutionStatus;
import org.wso2.carbon.identity.action.execution.model.ActionType;
import org.wso2.carbon.identity.action.execution.model.Error;
import org.wso2.carbon.identity.action.execution.model.ErrorStatus;
import org.wso2.carbon.identity.action.execution.model.FailedStatus;
import org.wso2.carbon.identity.action.execution.model.Failure;
import org.wso2.carbon.identity.action.execution.model.Success;
import org.wso2.carbon.identity.action.execution.model.SuccessStatus;
import org.wso2.carbon.identity.common.testng.WithCarbonHome;
import org.wso2.carbon.identity.core.context.IdentityContext;
import org.wso2.carbon.identity.core.context.model.Flow;
import org.wso2.carbon.identity.core.model.IdentityEventListenerConfig;
import org.wso2.carbon.identity.core.util.IdentityCoreConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.user.action.api.service.UserActionExecutor;
import org.wso2.carbon.identity.user.action.internal.factory.UserActionExecutorFactory;
import org.wso2.carbon.identity.user.action.internal.listener.ActionUserOperationEventListener;
import org.wso2.carbon.user.core.UserStoreClientException;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.Secret;
import org.wso2.carbon.utils.UnsupportedSecretTypeException;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.assertFalse;
import static org.wso2.carbon.identity.user.action.api.constant.UserActionError.PRE_UPDATE_PASSWORD_ACTION_EXECUTION_ERROR;
import static org.wso2.carbon.identity.user.action.api.constant.UserActionError.PRE_UPDATE_PASSWORD_ACTION_EXECUTION_FAILED;
import static org.wso2.carbon.identity.user.action.api.constant.UserActionError.PRE_UPDATE_PASSWORD_ACTION_SERVER_ERROR;
import static org.wso2.carbon.identity.user.action.api.constant.UserActionError.PRE_UPDATE_PASSWORD_ACTION_UNSUPPORTED_SECRET;

/**
 * Unit tests for ActionUserOperationEventListener.
 */
@WithCarbonHome
public class ActionUserOperationEventListenerTest {

    private static final int DEFAULT_LISTENER_ORDER = 10000;
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";

    private UserStoreManager userStoreManager;
    private UserActionExecutor mockExecutor;
    private MockedStatic<UserCoreUtil> userCoreUtil;

    private ActionUserOperationEventListener listener;

    @BeforeMethod
    public void setUp() {

        userStoreManager = mock(UserStoreManager.class);
        mockExecutor = mock(UserActionExecutor.class);
        userCoreUtil = mockStatic(UserCoreUtil.class);
        listener = new ActionUserOperationEventListener();
        userCoreUtil.when(() -> UserCoreUtil.getDomainName(any())).thenReturn("PRIMARY");
        IdentityContext.getThreadLocalIdentityContext().setFlow(new Flow.Builder()
                .name(Flow.Name.PASSWORD_RESET)
                .initiatingPersona(Flow.InitiatingPersona.USER)
                .build());
    }

    @AfterMethod
    public void tearDown() {

        userCoreUtil.close();
        UserActionExecutorFactory.unregisterUserActionExecutor(mockExecutor);
        IdentityContext.destroyCurrentContext();
    }

    @Test
    public void testGetExecutionOrderId() {

        IdentityEventListenerConfig mockConfig = mock(IdentityEventListenerConfig.class);
        try (MockedStatic<IdentityUtil> identityUtilMockedStatic = mockStatic(IdentityUtil.class)) {
            identityUtilMockedStatic.when(() -> IdentityUtil.readEventListenerProperty(any(), any()))
                    .thenReturn(mockConfig);
            doReturn(5000).when(mockConfig).getOrder();
            Assert.assertEquals(listener.getExecutionOrderId(), 5000);

            doReturn(IdentityCoreConstants.EVENT_LISTENER_ORDER_ID).when(mockConfig).getOrder();
            Assert.assertEquals(listener.getExecutionOrderId(), DEFAULT_LISTENER_ORDER);
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionWithDisabledListener()
            throws UserStoreException, UnsupportedSecretTypeException {

        IdentityEventListenerConfig mockConfig = mock(IdentityEventListenerConfig.class);
        try (MockedStatic<IdentityUtil> identityUtilMockedStatic = mockStatic(IdentityUtil.class)) {
            identityUtilMockedStatic.when(() -> IdentityUtil.readEventListenerProperty(any(), any()))
                    .thenReturn(mockConfig);
            doReturn("false").when(mockConfig).getEnable();
            Assert.assertTrue(listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD),
                    userStoreManager));
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionSuccess()
            throws UserStoreException, ActionExecutionException, UnsupportedSecretTypeException {

        ActionExecutionStatus<Success> successStatus =
                new SuccessStatus.Builder().setResponseContext(Collections.emptyMap()).build();
        doReturn(successStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        boolean result = listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD),
                userStoreManager);
        Assert.assertTrue(result, "The method should return true for successful execution.");
    }

    @Test
    public void testPreUpdatePasswordActionExecutionFailure() throws ActionExecutionException {

        Failure failureResponse = new Failure("FailureReason", "FailureDescription");
        ActionExecutionStatus<Failure> failedStatus = new FailedStatus(failureResponse);
        doReturn(failedStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        try {
            listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD), userStoreManager);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UserStoreClientException);
            Assert.assertEquals(e.getMessage(), "FailureReason. FailureDescription");
            Assert.assertEquals(((UserStoreClientException) e).getErrorCode(),
                    PRE_UPDATE_PASSWORD_ACTION_EXECUTION_FAILED);
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionFailureWithoutDescription() throws ActionExecutionException {

        Failure failureResponse = new Failure("FailureReason", null);
        ActionExecutionStatus<Failure> failedStatus = new FailedStatus(failureResponse);
        doReturn(failedStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        try {
            listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD), userStoreManager);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UserStoreClientException);
            Assert.assertEquals(e.getMessage(), "FailureReason");
            Assert.assertEquals(((UserStoreClientException) e).getErrorCode(),
                    PRE_UPDATE_PASSWORD_ACTION_EXECUTION_FAILED);
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionError() throws ActionExecutionException {

        Error errorResponse = new Error("ErrorMessage", "ErrorDescription");
        ActionExecutionStatus<Error> errorStatus = new ErrorStatus(errorResponse);
        doReturn(errorStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        try {
            listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD), userStoreManager);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UserStoreException);
            Assert.assertEquals(e.getMessage(), "ErrorMessage. ErrorDescription");
            Assert.assertEquals(((UserStoreException) e).getErrorCode(), PRE_UPDATE_PASSWORD_ACTION_EXECUTION_ERROR);
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionWithUnsupportedSecret() throws ActionExecutionException {

        Error errorResponse = new Error("ErrorMessage", "ErrorDescription");
        ActionExecutionStatus<Error> errorStatus = new ErrorStatus(errorResponse);
        doReturn(errorStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        try {
            listener.doPreUpdateCredentialByAdminWithID(USER_NAME, 10, userStoreManager);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UserStoreException);
            Assert.assertEquals(e.getMessage(), "Credential is not in the expected format.");
            Assert.assertEquals(((UserStoreException) e).getErrorCode(), PRE_UPDATE_PASSWORD_ACTION_UNSUPPORTED_SECRET);
        }
    }

    @Test
    public void testPreUpdatePasswordActionExecutionWithUnknownStatus()
            throws UserStoreException, ActionExecutionException, UnsupportedSecretTypeException {

        ActionExecutionStatus<?> unknownStatus = mock(ActionExecutionStatus.class);
        doReturn(null).when(unknownStatus).getStatus();
        doReturn(unknownStatus).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        assertFalse(listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD),
                userStoreManager));
    }

    @Test
    public void testPreUpdatePasswordActionExecutionWithActionExecutionException() throws ActionExecutionException {

        doThrow(new ActionExecutionException("Execution error")).when(mockExecutor).execute(any(), any());
        doReturn(ActionType.PRE_UPDATE_PASSWORD).when(mockExecutor).getSupportedActionType();
        UserActionExecutorFactory.registerUserActionExecutor(mockExecutor);

        try {
            listener.doPreUpdateCredentialByAdminWithID(USER_NAME, Secret.getSecret(PASSWORD), userStoreManager);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UserStoreException);
            Assert.assertEquals(e.getMessage(), "Error while executing pre update password action.");
            Assert.assertEquals(((UserStoreException) e).getErrorCode(), PRE_UPDATE_PASSWORD_ACTION_SERVER_ERROR);
        }
    }
}
