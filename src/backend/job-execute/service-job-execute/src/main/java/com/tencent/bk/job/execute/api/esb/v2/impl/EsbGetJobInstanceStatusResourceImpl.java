/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.execute.api.esb.v2.impl;

import com.google.common.collect.Lists;
import com.tencent.bk.job.common.constant.ErrorCode;
import com.tencent.bk.job.common.esb.metrics.EsbApiTimed;
import com.tencent.bk.job.common.esb.model.EsbResp;
import com.tencent.bk.job.common.i18n.service.MessageI18nService;
import com.tencent.bk.job.common.model.ValidateResult;
import com.tencent.bk.job.execute.api.esb.v2.EsbGetJobInstanceStatusResource;
import com.tencent.bk.job.execute.common.constants.RunStatusEnum;
import com.tencent.bk.job.execute.model.GseTaskIpLogDTO;
import com.tencent.bk.job.execute.model.StepInstanceBaseDTO;
import com.tencent.bk.job.execute.model.TaskInstanceDTO;
import com.tencent.bk.job.execute.model.esb.v2.EsbIpStatusDTO;
import com.tencent.bk.job.execute.model.esb.v2.EsbJobInstanceStatusDTO;
import com.tencent.bk.job.execute.model.esb.v2.request.EsbGetJobInstanceStatusRequest;
import com.tencent.bk.job.execute.service.GseTaskLogService;
import com.tencent.bk.job.execute.service.TaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class EsbGetJobInstanceStatusResourceImpl
    extends JobQueryCommonProcessor
    implements EsbGetJobInstanceStatusResource {

    private final TaskInstanceService taskInstanceService;
    private final GseTaskLogService gseTaskLogService;
    private final MessageI18nService i18nService;

    public EsbGetJobInstanceStatusResourceImpl(MessageI18nService i18nService, GseTaskLogService gseTaskLogService,
                                               TaskInstanceService taskInstanceService) {
        this.i18nService = i18nService;
        this.gseTaskLogService = gseTaskLogService;
        this.taskInstanceService = taskInstanceService;
    }

    @Override
    @EsbApiTimed(value = "esb.api", extraTags = {"api_name", "v2_get_job_instance_status"})
    public EsbResp<EsbJobInstanceStatusDTO> getJobInstanceStatusUsingPost(String lang,
                                                                          EsbGetJobInstanceStatusRequest request) {
        ValidateResult checkResult = checkRequest(request);
        if (!checkResult.isPass()) {
            log.warn("Get job instance status request is illegal!");
            return EsbResp.buildCommonFailResp(i18nService, checkResult);
        }

        long taskInstanceId = request.getTaskInstanceId();

        TaskInstanceDTO taskInstance = taskInstanceService.getTaskInstance(request.getTaskInstanceId());
        EsbResp authResult = authViewTaskInstance(request.getUserName(), request.getAppId(), taskInstance);
        if (!authResult.getCode().equals(EsbResp.SUCCESS_CODE)) {
            return authResult;
        }


        List<StepInstanceBaseDTO> stepInstances = taskInstanceService.listStepInstanceByTaskInstanceId(taskInstanceId);
        if (stepInstances == null || stepInstances.isEmpty()) {
            log.warn("Get job instance status by taskInstanceId:{}, stepInstanceList is empty!", taskInstanceId);
            return EsbResp.buildCommonFailResp(ErrorCode.STEP_INSTANCE_NOT_EXIST, i18nService);
        }

        Map<Long, List<EsbIpStatusDTO>> stepIpResultMap = getStepIpResult(stepInstances);

        return EsbResp.buildSuccessResp(buildEsbJobInstanceStatusDTO(taskInstance, stepInstances, stepIpResultMap));
    }

    private ValidateResult checkRequest(EsbGetJobInstanceStatusRequest request) {
        if (request.getAppId() == null || request.getAppId() < 1) {
            log.warn("App is empty or illegal, appId={}", request.getAppId());
            return ValidateResult.fail(ErrorCode.MISSING_OR_ILLEGAL_PARAM_WITH_PARAM_NAME, "bk_biz_id");
        }
        if (request.getTaskInstanceId() == null || request.getTaskInstanceId() < 1) {
            log.warn("TaskInstanceId is empty or illegal, taskInstanceId={}", request.getTaskInstanceId());
            return ValidateResult.fail(ErrorCode.MISSING_OR_ILLEGAL_PARAM_WITH_PARAM_NAME, "job_instance_id");
        }
        return ValidateResult.pass();
    }

    private Map<Long, List<EsbIpStatusDTO>> getStepIpResult(List<StepInstanceBaseDTO> stepInstanceList) {
        Map<Long, List<EsbIpStatusDTO>> stepIpResult = new HashMap<>();
        for (StepInstanceBaseDTO stepInstance : stepInstanceList) {
            List<GseTaskIpLogDTO> ipLogList = gseTaskLogService.getIpLog(stepInstance.getId(),
                stepInstance.getExecuteCount(), true);
            List<EsbIpStatusDTO> ipResultList = Lists.newArrayList();
            for (GseTaskIpLogDTO ipLog : ipLogList) {
                EsbIpStatusDTO ipStatus = new EsbIpStatusDTO();
                ipStatus.setIp(ipLog.getIp());
                ipStatus.setCloudAreaId(ipLog.getCloudAreaId());
                ipStatus.setStatus(ipLog.getStatus());
                ipResultList.add(ipStatus);
            }
            stepIpResult.put(stepInstance.getId(), ipResultList);
        }
        return stepIpResult;
    }

    private EsbJobInstanceStatusDTO buildEsbJobInstanceStatusDTO(TaskInstanceDTO taskInstance,
                                                                 List<StepInstanceBaseDTO> stepInstances,
                                                                 Map<Long, List<EsbIpStatusDTO>> stepIpResultMap) {
        EsbJobInstanceStatusDTO jobInstanceStatus = new EsbJobInstanceStatusDTO();
        jobInstanceStatus.setIsFinished(!taskInstance.getStatus().equals(RunStatusEnum.BLANK.getValue())
            && !taskInstance.getStatus().equals(RunStatusEnum.RUNNING.getValue()));

        EsbJobInstanceStatusDTO.JobInstance jobInstance = new EsbJobInstanceStatusDTO.JobInstance();
        jobInstance.setAppId(taskInstance.getAppId());
        jobInstance.setCurrentStepId(taskInstance.getCurrentStepId());
        jobInstance.setId(taskInstance.getId());
        jobInstance.setName(taskInstance.getName());
        jobInstance.setOperator(taskInstance.getOperator());
        jobInstance.setCreateTime(taskInstance.getCreateTime());
        jobInstance.setStartTime(taskInstance.getStartTime());
        jobInstance.setEndTime(taskInstance.getEndTime());
        jobInstance.setStartWay(taskInstance.getStartupMode());
        jobInstance.setStatus(taskInstance.getStatus());
        jobInstance.setTaskId(taskInstance.getTaskId());
        jobInstance.setTotalTime(taskInstance.getTotalTime());
        jobInstanceStatus.setJobInstance(jobInstance);

        List<EsbJobInstanceStatusDTO.Block> blocks = new ArrayList<>();
        for (StepInstanceBaseDTO stepInstance : stepInstances) {
            EsbJobInstanceStatusDTO.Block block = new EsbJobInstanceStatusDTO.Block();

            List<EsbJobInstanceStatusDTO.StepInst> stepInsts = new ArrayList<>(1);
            EsbJobInstanceStatusDTO.StepInst stepInst = new EsbJobInstanceStatusDTO.StepInst();
            stepInst.setId(stepInstance.getId());
            stepInst.setName(stepInstance.getName());
            stepInst.setCreateTime(stepInstance.getCreateTime());
            stepInst.setEndTime(stepInstance.getEndTime());
            stepInst.setStartTime(stepInstance.getStartTime());
            stepInst.setType(stepInstance.getExecuteType());
            stepInst.setOperator(stepInstance.getOperator());
            stepInst.setExecuteCount(stepInstance.getExecuteCount());
            stepInst.setStatus(stepInstance.getStatus());
            stepInst.setStepId(stepInstance.getStepId());
            stepInst.setTotalTime(stepInstance.getTotalTime());
            List<EsbIpStatusDTO> stepIpResult = stepIpResultMap.get(stepInstance.getId());
            stepInst.setStepIpResult(stepIpResult);
            stepInsts.add(stepInst);

            block.setStepInstances(stepInsts);
            blocks.add(block);
        }
        jobInstanceStatus.setBlocks(blocks);

        return jobInstanceStatus;
    }

    @Override
    public EsbResp<EsbJobInstanceStatusDTO> getJobInstanceStatus(String lang, String appCode, String username,
                                                                 Long appId, Long taskInstanceId) {
        EsbGetJobInstanceStatusRequest req = new EsbGetJobInstanceStatusRequest();
        req.setAppCode(appCode);
        req.setUserName(username);
        req.setAppId(appId);
        req.setTaskInstanceId(taskInstanceId);
        return getJobInstanceStatusUsingPost(lang, req);
    }
}
