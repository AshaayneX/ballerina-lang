// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/task;
import ballerina/runtime;

int count = 0;

function getCount() returns int {
    return count;
}

function triggerTimer() {
    task:TimerConfiguration configuration = {
        intervalInMillis: 1000,
        initialDelayInMillis: 0
    };

    task:Scheduler timer = new(configuration);
    var result = timer.attach(timerService);
    checkpanic timer.start();
}

service timerService = service {
    resource function onTrigger() {
        count = count + 1;
    }
};