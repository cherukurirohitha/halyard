/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.HaServiceService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/deploymentEnvironment/haServices")
public class HaServiceController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  HaServiceService haServiceService;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/{serviceName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, HaService> get(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<HaService> builder = new StaticRequestBuilder<>(
        () -> haServiceService.getHaService(deploymentName, serviceName));

    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> haServiceService.validateHaService(deploymentName, serviceName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get the " + serviceName + " high availability service configuration");
  }

  @RequestMapping(value = "/{serviceName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setHaService(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawHaService) {
    HaService haService = objectMapper.convertValue(
        rawHaService,
        HaServices.translateHaServiceType(serviceName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> haService.stageLocalFiles(configPath));
    builder.setUpdate(() -> haServiceService.setHaService(deploymentName, haService));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> haServiceService.validateHaService(deploymentName, serviceName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + serviceName + " high availability service configuration");
  }

  @RequestMapping(value = "/{serviceName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> haServiceService.setEnabled(deploymentName, serviceName, enabled));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> haServiceService.validateHaService(deploymentName, serviceName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + serviceName + " high availability service configuration");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<HaService>> haServices(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<HaService>> builder = new StaticRequestBuilder<>(
        () -> haServiceService.getAllHaServices(deploymentName));

    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> haServiceService.validateAllHaServices(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all high availability service configurations");
  }
}
