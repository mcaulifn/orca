/*
 * Copyright 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

class WaitForRecreatedGoogleInstancesTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    List<String> instanceIds = stage.context."terminate.instance.ids"
    List<Long> launchTimes = stage.context.launchTimes

    if (!instanceIds || !launchTimes || instanceIds.size() != launchTimes.size()) {
      return new DefaultTaskResult(ExecutionStatus.FAILED)
    }

    Map<String, Long> launchTimesMap = new HashMap<String, Long>()

    for (int i = 0; i < instanceIds.size; i++) {
      launchTimesMap[instanceIds[i]] = launchTimes[i]
    }

    def notAllRecreated = instanceIds.find { String instanceId ->
      try {
        def response = oortService.getInstance(stage.context.credentials, stage.context.region, instanceId)
        def instanceQueryResult = objectMapper.readValue(response.body.in().text, Map)

        return !instanceQueryResult || instanceQueryResult.launchTime == launchTimesMap[instanceId]
      } catch (RetrofitError e) {
        // 404 causes this Error to be thrown. If the cache was refreshed while the instance was non-existent,
        // oort will 404.
        return true
      }
    }

    def status = notAllRecreated ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED

    new DefaultTaskResult(status)
  }
}
