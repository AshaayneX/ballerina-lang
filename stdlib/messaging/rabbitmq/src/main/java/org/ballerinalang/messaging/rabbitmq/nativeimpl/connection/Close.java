/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.messaging.rabbitmq.nativeimpl.connection;

import com.rabbitmq.client.Connection;
import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.jvm.Strand;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.messaging.rabbitmq.RabbitMQConnectorException;
import org.ballerinalang.messaging.rabbitmq.RabbitMQConstants;
import org.ballerinalang.messaging.rabbitmq.RabbitMQUtils;
import org.ballerinalang.messaging.rabbitmq.util.ConnectionUtils;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;

/**
 * Closes a RabbitMQ Connection.
 *
 * @since 0.995.0
 */
@BallerinaFunction(
        orgName = RabbitMQConstants.ORG_NAME,
        packageName = RabbitMQConstants.RABBITMQ,
        functionName = "close",
        receiver = @Receiver(type = TypeKind.OBJECT,
                structType = RabbitMQConstants.CONNECTION_OBJECT,
                structPackage = RabbitMQConstants.PACKAGE_RABBITMQ),
        isPublic = true
)
public class Close extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context context) {
    }

    public static Object close(Strand strand, ObjectValue connectionObjectValue, Object closeCode, Object closeMessage,
                               Object timeout) {
        Connection connection = (Connection)
                connectionObjectValue.getNativeData(RabbitMQConstants.CONNECTION_NATIVE_OBJECT);
        try {
            ConnectionUtils.handleCloseConnection(connection, closeCode, closeMessage, timeout);
        } catch (RabbitMQConnectorException exception) {
            return RabbitMQUtils.returnErrorValue
                    (RabbitMQConstants.CLOSE_CONNECTION_ERROR + exception.getDetail());
        }
        connectionObjectValue.addNativeData(RabbitMQConstants.CONNECTION_NATIVE_OBJECT, null);
        return null;
    }
}