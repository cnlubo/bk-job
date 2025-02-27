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

package com.tencent.bk.job.crontab.api.iam.impl;

import com.tencent.bk.job.common.iam.constant.ResourceId;
import com.tencent.bk.job.common.iam.service.BaseIamCallbackService;
import com.tencent.bk.job.common.iam.util.IamRespUtil;
import com.tencent.bk.job.common.model.BaseSearchCondition;
import com.tencent.bk.job.common.model.PageData;
import com.tencent.bk.job.crontab.api.iam.IamCallbackController;
import com.tencent.bk.job.crontab.model.dto.CronJobInfoDTO;
import com.tencent.bk.job.crontab.service.CronJobService;
import com.tencent.bk.sdk.iam.dto.PathInfoDTO;
import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO;
import com.tencent.bk.sdk.iam.dto.callback.request.IamSearchCondition;
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO;
import com.tencent.bk.sdk.iam.dto.callback.response.FetchInstanceInfoResponseDTO;
import com.tencent.bk.sdk.iam.dto.callback.response.InstanceInfoDTO;
import com.tencent.bk.sdk.iam.dto.callback.response.ListInstanceResponseDTO;
import com.tencent.bk.sdk.iam.dto.callback.response.SearchInstanceResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class IamCallbackControllerImpl extends BaseIamCallbackService implements IamCallbackController {

    private final CronJobService cronJobService;

    @Autowired
    public IamCallbackControllerImpl(CronJobService cronJobService) {
        this.cronJobService = cronJobService;
    }

    private Pair<CronJobInfoDTO, BaseSearchCondition> getBasicQueryCondition(CallbackRequestDTO callbackRequest) {
        IamSearchCondition searchCondition = IamSearchCondition.fromReq(callbackRequest);
        BaseSearchCondition baseSearchCondition = new BaseSearchCondition();
        baseSearchCondition.setStart(searchCondition.getStart().intValue());
        baseSearchCondition.setLength(searchCondition.getLength().intValue());

        CronJobInfoDTO cronJobQuery = new CronJobInfoDTO();
        cronJobQuery.setAppId(searchCondition.getAppIdList().get(0));
        return Pair.of(cronJobQuery, baseSearchCondition);
    }

    private InstanceInfoDTO convert(CronJobInfoDTO cronJobInfo) {
        InstanceInfoDTO instanceInfo = new InstanceInfoDTO();
        instanceInfo.setId(String.valueOf(cronJobInfo.getId()));
        instanceInfo.setDisplayName(cronJobInfo.getName());
        return instanceInfo;
    }

    @Override
    protected SearchInstanceResponseDTO searchInstanceResp(
        CallbackRequestDTO callbackRequest
    ) {
        Pair<CronJobInfoDTO, BaseSearchCondition> basicQueryCond = getBasicQueryCondition(callbackRequest);
        CronJobInfoDTO cronJobQuery = basicQueryCond.getLeft();
        BaseSearchCondition baseSearchCondition = basicQueryCond.getRight();

        cronJobQuery.setName(callbackRequest.getFilter().getKeyword());
        PageData<CronJobInfoDTO> cronJobInfoPageData =
            cronJobService.listPageCronJobInfos(cronJobQuery, baseSearchCondition);

        return IamRespUtil.getSearchInstanceRespFromPageData(cronJobInfoPageData, this::convert);
    }

    @Override
    protected ListInstanceResponseDTO listInstanceResp(
        CallbackRequestDTO callbackRequest
    ) {
        Pair<CronJobInfoDTO, BaseSearchCondition> basicQueryCond = getBasicQueryCondition(callbackRequest);
        CronJobInfoDTO cronJobQuery = basicQueryCond.getLeft();
        BaseSearchCondition baseSearchCondition = basicQueryCond.getRight();

        PageData<CronJobInfoDTO> cronJobInfoPageData =
            cronJobService.listPageCronJobInfos(cronJobQuery, baseSearchCondition);

        return IamRespUtil.getListInstanceRespFromPageData(cronJobInfoPageData, this::convert);
    }

    @Override
    protected CallbackBaseResponseDTO fetchInstanceResp(
        CallbackRequestDTO callbackRequest
    ) {
        IamSearchCondition searchCondition = IamSearchCondition.fromReq(callbackRequest);
        List<Object> instanceAttributeInfoList = new ArrayList<>();
        List<Long> cronJobIdList = new ArrayList<>();
        for (String instanceId : searchCondition.getIdList()) {
            try {
                long id = Long.parseLong(instanceId);
                cronJobIdList.add(id);
            } catch (NumberFormatException e) {
                log.error("Parse object id failed!|{}", instanceId, e);
            }
        }
        Map<Long, CronJobInfoDTO> cronJobInfoMap = cronJobService.getCronJobInfoMapByIds(cronJobIdList);
        for (Long id : cronJobIdList) {
            CronJobInfoDTO cronJobInfoDTO = cronJobInfoMap.get(id);
            if (cronJobInfoDTO == null) {
                return getNotFoundRespById(id.toString());
            }
            // 拓扑路径构建
            List<PathInfoDTO> path = new ArrayList<>();
            PathInfoDTO rootNode = new PathInfoDTO();
            rootNode.setType(ResourceId.APP);
            rootNode.setId(cronJobInfoDTO.getAppId().toString());
            PathInfoDTO cronJobNode = new PathInfoDTO();
            cronJobNode.setType(ResourceId.CRON);
            cronJobNode.setId(cronJobInfoDTO.getId().toString());
            rootNode.setChild(cronJobNode);
            path.add(rootNode);
            // 实例组装
            InstanceInfoDTO instanceInfo = new InstanceInfoDTO();
            instanceInfo.setId(id.toString());
            instanceInfo.setDisplayName(cronJobInfoDTO.getName());
            instanceInfo.setPath(path);
            instanceAttributeInfoList.add(instanceInfo);
        }

        FetchInstanceInfoResponseDTO fetchInstanceInfoResponse = new FetchInstanceInfoResponseDTO();
        fetchInstanceInfoResponse.setCode(0L);
        fetchInstanceInfoResponse.setData(instanceAttributeInfoList);
        return fetchInstanceInfoResponse;
    }

    @Override
    public CallbackBaseResponseDTO callback(CallbackRequestDTO callbackRequest) {
        return baseCallback(callbackRequest);
    }
}
