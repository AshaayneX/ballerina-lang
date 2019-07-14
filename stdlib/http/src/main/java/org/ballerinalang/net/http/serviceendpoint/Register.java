/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.http.serviceendpoint;

import org.ballerinalang.bre.Context;
import org.ballerinalang.connector.api.BLangConnectorSPIUtil;
import org.ballerinalang.connector.api.ParamDetail;
import org.ballerinalang.connector.api.Service;
import org.ballerinalang.connector.api.Struct;
import org.ballerinalang.jvm.Strand;
import org.ballerinalang.jvm.types.AttachedFunction;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.net.http.BHTTPServicesRegistry;
import org.ballerinalang.net.http.BWebSocketService;
import org.ballerinalang.net.http.BWebSocketServicesRegistry;
import org.ballerinalang.net.http.HTTPServicesRegistry;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.WebSocketConstants;
import org.ballerinalang.net.http.WebSocketService;
import org.ballerinalang.net.http.WebSocketServicesRegistry;

import static org.ballerinalang.net.http.HttpConstants.HTTP_LISTENER_ENDPOINT;

/**
 * Register a service to the listener.
 *
 * @since 0.966
 */

@BallerinaFunction(
        orgName = "ballerina", packageName = "http",
        functionName = "register",
        receiver = @Receiver(type = TypeKind.OBJECT, structType = HTTP_LISTENER_ENDPOINT,
                             structPackage = "ballerina/http"),
        args = {@Argument(name = "serviceType", type = TypeKind.SERVICE),
                @Argument(name = "annotationData", type = TypeKind.MAP)},
        isPublic = true
)
public class Register extends AbstractHttpNativeFunction {

    @Override
    public void execute(Context context) {
        Service service = BLangConnectorSPIUtil.getServiceRegistered(context);
        Struct serviceEndpoint = BLangConnectorSPIUtil.getConnectorEndpointStruct(context);

        BHTTPServicesRegistry httpServicesRegistry = getHttpServicesRegistry(serviceEndpoint);
        BWebSocketServicesRegistry webSocketServicesRegistry = getWebSocketServicesRegistry(serviceEndpoint);

        ParamDetail param;
        if (service.getResources().length > 0 && (param = service.getResources()[0].getParamDetails().get(0)) != null) {
            String callerType = param.getVarType().toString();
            if (HttpConstants.HTTP_CALLER_NAME.equals(callerType)) {
                httpServicesRegistry.registerService(service);
            } else if (WebSocketConstants.FULL_WEBSOCKET_CALLER_NAME.equals(callerType)) {
                BWebSocketService webSocketService = new BWebSocketService(service);
                webSocketServicesRegistry.registerService(webSocketService);
            }
        } else {
            httpServicesRegistry.registerService(service);
        }
        context.setReturnValues();
    }

    public static Object register(Strand strand, ObjectValue serviceEndpoint, ObjectValue service,
                                  Object annotationData) {
        HTTPServicesRegistry httpServicesRegistry = getHttpServicesRegistry(serviceEndpoint);
        WebSocketServicesRegistry webSocketServicesRegistry = getWebSocketServicesRegistry(serviceEndpoint);
        httpServicesRegistry.setScheduler(strand.scheduler);

        BType param;
        AttachedFunction[] resourceList = service.getType().getAttachedFunctions();
        if (resourceList.length > 0 && (param = resourceList[0].getParameterType()[0]) != null) {
            String callerType = param.getQualifiedName();
            if (HttpConstants.HTTP_CALLER_NAME.equals(callerType)) { // TODO fix should work with equals - rajith
                httpServicesRegistry.registerService(service);
            } else if (WebSocketConstants.FULL_WEBSOCKET_CALLER_NAME.equals(callerType)) {
                WebSocketService webSocketService = new WebSocketService(service, strand.scheduler);
                webSocketServicesRegistry.registerService(webSocketService);
            }
        } else {
            httpServicesRegistry.registerService(service);
        }
        return null;
    }
}