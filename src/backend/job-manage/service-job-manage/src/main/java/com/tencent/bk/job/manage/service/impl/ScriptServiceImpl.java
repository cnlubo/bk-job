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

package com.tencent.bk.job.manage.service.impl;

import com.tencent.bk.job.common.constant.ErrorCode;
import com.tencent.bk.job.common.exception.ServiceException;
import com.tencent.bk.job.common.i18n.service.MessageI18nService;
import com.tencent.bk.job.common.iam.constant.ActionId;
import com.tencent.bk.job.common.iam.constant.ResourceTypeEnum;
import com.tencent.bk.job.common.iam.exception.InSufficientPermissionException;
import com.tencent.bk.job.common.iam.model.AuthResult;
import com.tencent.bk.job.common.iam.model.PermissionActionResource;
import com.tencent.bk.job.common.iam.service.AuthService;
import com.tencent.bk.job.common.model.BaseSearchCondition;
import com.tencent.bk.job.common.model.PageData;
import com.tencent.bk.job.common.util.JobUUID;
import com.tencent.bk.job.common.util.date.DateUtils;
import com.tencent.bk.job.common.util.json.JsonUtils;
import com.tencent.bk.job.common.constant.JobConstants;
import com.tencent.bk.job.manage.common.consts.JobResourceStatusEnum;
import com.tencent.bk.job.manage.common.consts.script.ScriptTypeEnum;
import com.tencent.bk.job.manage.dao.*;
import com.tencent.bk.job.manage.dao.template.TaskTemplateDAO;
import com.tencent.bk.job.manage.model.dto.*;
import com.tencent.bk.job.manage.model.dto.script.ScriptCitedTaskPlanDTO;
import com.tencent.bk.job.manage.model.dto.script.ScriptCitedTaskTemplateDTO;
import com.tencent.bk.job.manage.model.dto.task.TaskTemplateInfoDTO;
import com.tencent.bk.job.manage.service.ScriptService;
import com.tencent.bk.job.manage.service.TagService;
import com.tencent.bk.job.manage.service.template.TaskTemplateService;
import com.tencent.bk.job.manage.service.template.impl.TemplateStatusUpdateService;
import com.tencent.bk.sdk.iam.util.PathBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @date 2019/09/19
 */
@Slf4j
@Service
public class ScriptServiceImpl implements ScriptService {

    private final ScriptDAO scriptDAO;
    private final TagService tagService;
    private final ScriptRelateTaskPlanDAO scriptRelateTaskPlanDAO;
    private final ScriptCitedTaskTemplateDAO scriptCitedTaskTemplateDAO;
    private final ScriptRelateJobTemplateDAO scriptRelateJobTemplateDAO;
    private final TaskScriptStepDAO taskScriptStepDAO;
    private final TaskTemplateDAO taskTemplateDAO;
    private final TemplateStatusUpdateService templateStatusUpdateService;
    private final AuthService authService;
    private final MessageI18nService i18nService;
    private DSLContext dslContext;
    private TaskTemplateService taskTemplateService;


    @Autowired
    public ScriptServiceImpl(
        DSLContext dslContext,
        ScriptDAO scriptDAO,
        TagService tagService,
        ScriptRelateTaskPlanDAO scriptRelateTaskPlanDAO,
        ScriptCitedTaskTemplateDAO scriptCitedTaskTemplateDAO,
        TaskTemplateService taskTemplateService,
        ScriptRelateJobTemplateDAO scriptRelateJobTemplateDAO,
        @Qualifier("TaskTemplateScriptStepDAOImpl") TaskScriptStepDAO taskScriptStepDAO,
        TaskTemplateDAO taskTemplateDAO,
        TemplateStatusUpdateService templateStatusUpdateService,
        AuthService authService,
        MessageI18nService i18nService) {
        this.dslContext = dslContext;
        this.scriptDAO = scriptDAO;
        this.tagService = tagService;
        this.scriptRelateTaskPlanDAO = scriptRelateTaskPlanDAO;
        this.scriptCitedTaskTemplateDAO = scriptCitedTaskTemplateDAO;
        this.taskTemplateService = taskTemplateService;
        this.scriptRelateJobTemplateDAO = scriptRelateJobTemplateDAO;
        this.taskScriptStepDAO = taskScriptStepDAO;
        this.taskTemplateDAO = taskTemplateDAO;
        this.templateStatusUpdateService = templateStatusUpdateService;
        this.authService = authService;
        this.i18nService = i18nService;
    }

    @Override
    public ScriptDTO getByScriptVersionId(Long scriptVersionId) throws ServiceException {
        ScriptDTO scriptDTO = scriptDAO.getScriptVersionById(scriptVersionId);
        if (scriptDTO != null) {
            setTagsFromScript(scriptDTO);
        }
        return scriptDTO;
    }

    private void setTagsFromScript(ScriptDTO scriptDTO) {
        if (scriptDTO.getTags() != null) {
            for (TagDTO tagDTO : scriptDTO.getTags()) {
                TagDTO tagInfo = tagService.getTagInfoById(scriptDTO.getAppId(), tagDTO.getId());
                if (tagInfo != null) {
                    tagDTO.setName(tagInfo.getName());
                }
            }
        }
    }

    @Override
    public ScriptDTO getScriptVersion(String operator, Long appId, Long scriptVersionId) throws ServiceException {
        ScriptDTO scriptDTO = scriptDAO.getScriptVersionById(scriptVersionId);
        checkGetScriptPermission(operator, appId, scriptDTO);
        setTagsFromScript(scriptDTO);
        return scriptDTO;
    }

    @Override
    public ScriptDTO getByScriptIdAndVersion(String operator, Long appId, String scriptId,
                                             String version) throws ServiceException {
        ScriptDTO scriptDTO = scriptDAO.getByScriptIdAndVersion(appId, scriptId, version);
        checkGetScriptPermission(operator, appId, scriptDTO);
        setTagsFromScript(scriptDTO);
        return scriptDTO;
    }

    @Override
    public ScriptDTO getScriptVersion(Long scriptVersionId) throws ServiceException {
        return scriptDAO.getScriptVersionById(scriptVersionId);
    }

    private void checkGetScriptPermission(String operator, Long appId, ScriptDTO script) throws ServiceException {
        if (script == null) {
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }
        if (!script.isPublicScript() && !script.getAppId().equals(appId)) {
            log.warn("Script:{} is not in app:{}", script.getId(), appId);
            throw new ServiceException(ErrorCode.SCRIPT_NOT_IN_APP);
        }
    }

    @Override
    public ScriptDTO getScript(String operator, Long appId, String scriptId) throws ServiceException {
        ScriptDTO script = scriptDAO.getScriptByScriptId(scriptId);
        checkGetScriptPermission(operator, appId, script);
        List<ScriptDTO> scripts = new ArrayList<>();
        scripts.add(script);
        setTagName(scripts);
        return script;
    }

    @Override
    public ScriptDTO getScriptByScriptId(String scriptId) throws ServiceException {
        ScriptDTO script = scriptDAO.getScriptByScriptId(scriptId);
        List<ScriptDTO> scripts = new ArrayList<>();
        scripts.add(script);
        setTagName(scripts);
        return script;
    }

    @Override
    public ScriptDTO getScriptWithoutTagByScriptId(String scriptId) throws ServiceException {
        return scriptDAO.getScriptByScriptId(scriptId);
    }

    @Override
    public List<ScriptDTO> listScriptVersion(String operator, Long appId, String scriptId) throws ServiceException {
        List<ScriptDTO> scripts = scriptDAO.listByScriptId(scriptId);
        if (scripts == null || scripts.isEmpty()) {
            return Collections.emptyList();
        }
        checkGetScriptPermission(operator, appId, scripts.get(0));
        //设置标签名称
        setTagName(scripts);
        return scripts;
    }

    private void setTagName(List<ScriptDTO> scripts) {
        // 设置标签名称.从DAO查询的结果仅包含tagId
        if (scripts != null && !scripts.isEmpty()) {
            Long appId = scripts.get(0).getAppId();
            List<TagDTO> tags = tagService.listTagsByAppId(appId);
            Map<Long, String> tagIdNameMap = new HashMap<>();
            if (tags != null && !tags.isEmpty()) {
                for (TagDTO tag : tags) {
                    tagIdNameMap.put(tag.getId(), tag.getName());
                }
            }
            for (ScriptDTO scriptDTO : scripts) {
                List<TagDTO> scriptTags = scriptDTO.getTags();
                if (scriptTags != null && !scriptTags.isEmpty()) {
                    for (TagDTO scriptTag : scriptTags) {
                        scriptTag.setName(tagIdNameMap.get(scriptTag.getId()));
                    }
                }
            }
        }
    }

    @Override
    public PageData<ScriptDTO> listPageScript(ScriptQueryDTO scriptCondition,
                                              BaseSearchCondition baseSearchCondition) throws ServiceException {
        PageData<ScriptDTO> pageData = scriptDAO.listPageScript(scriptCondition, baseSearchCondition);
        //设置标签名称
        List<ScriptDTO> scripts = pageData.getData();
        setTagName(scripts);
        return pageData;
    }


    @Override
    public ScriptDTO saveScript(String operator, Long appId, ScriptDTO script) throws ServiceException {
        log.info("Begin to save script, operator={}, appId={}, scriptName={}", operator, appId, script.getName());
        long targetAppId = appId;
        if (script.isPublicScript()) {
            // 公共脚本业务ID为0
            targetAppId = JobConstants.PUBLIC_APP_ID;
            script.setAppId(targetAppId);
        }

        List<TagDTO> tags = script.getTags();
        if (tags != null && !tags.isEmpty()) {
            List<TagDTO> newTags = tagService.createNewTagIfNotExist(tags, targetAppId, script.getLastModifyUser());
            script.setTags(newTags);
        }
        // 默认为未上线状态
        script.setStatus(JobResourceStatusEnum.DRAFT.getValue());

        Long scriptVersionId = script.getScriptVersionId();
        if (StringUtils.isNotBlank(script.getId())) {
            // 更新当前版本
            if (script.getScriptVersionId() != null && script.getScriptVersionId() > 0) {
                ScriptDTO scriptVersionToBeUpdate = scriptDAO.getScriptVersionById(script.getScriptVersionId());
                if (scriptVersionToBeUpdate == null) {
                    throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
                }
                if (!scriptVersionToBeUpdate.getStatus().equals(JobResourceStatusEnum.DRAFT.getValue())) {
                    log.warn("Script status is not draft, can not update.scriptVersionId={}",
                        script.getScriptVersionId());
                    throw new ServiceException(ErrorCode.UNSUPPORTED_OPERATION);
                }
                scriptDAO.updateScriptVersion(script.getLastModifyUser(), script.getScriptVersionId(), script);
            } else {
                // 新增脚本版本
                if (scriptDAO.isExistDuplicateVersion(script.getId(), script.getVersion())) {
                    log.warn("Script version:{} is exist, scriptId:{}", script.getVersion(), script.getId());
                    throw new ServiceException(ErrorCode.SCRIPT_VERSION_NAME_EXIST);
                }
                scriptVersionId = scriptDAO.saveScriptVersion(script);
                scriptDAO.updateScript(script);
            }
        } else {
            //脚本不存在，新增脚本
            boolean isNameDuplicate = scriptDAO.isExistDuplicateName(targetAppId, script.getName());
            if (isNameDuplicate) {
                log.warn("The script name:{} is exist for app:{}", script.getName(), targetAppId);
                throw new ServiceException(ErrorCode.SCRIPT_NAME_DUPLICATE);
            }
            script.setId(JobUUID.getUUID());
            scriptDAO.saveScript(script);
            scriptVersionId = scriptDAO.saveScriptVersion(script);
        }
        return scriptDAO.getScriptVersionById(scriptVersionId);
    }

    private Long getTimeOrDefault(Long time) {
        if (time == null) {
            return DateUtils.currentTimeMillis();
        }
        return time;
    }

    @Override
    public Pair<String, Long> createScriptWithVersionId(
        String operator,
        Long appId,
        ScriptDTO script,
        Long createTime,
        Long lastModifyTime
    ) throws ServiceException {
        log.info("Begin to createScriptWithVersionId, operator={}, appId={}, script={}, createTime={}, " +
            "lastModifyTime={}", operator, appId, JsonUtils.toJson(script), createTime, lastModifyTime);
        createTime = getTimeOrDefault(createTime);
        lastModifyTime = getTimeOrDefault(lastModifyTime);
        long targetAppId = appId;
        if (script.isPublicScript()) {
            // 公共脚本业务ID为0
            targetAppId = JobConstants.PUBLIC_APP_ID;
            script.setAppId(targetAppId);
        }

        List<TagDTO> tags = script.getTags();
        if (tags != null && !tags.isEmpty()) {
            List<TagDTO> newTags = tagService.createNewTagIfNotExist(tags, targetAppId, script.getLastModifyUser());
            script.setTags(newTags);
        }
        // 默认为未上线状态
        Integer status = script.getStatus();
        if (status == null || status < 0) {
            script.setStatus(JobResourceStatusEnum.DRAFT.getValue());
        }

        if (script.getScriptVersionId() != null && script.getScriptVersionId() > 0) {
            if (scriptDAO.isExistDuplicateId(script.getScriptVersionId())) {
                log.warn("scriptVersionId:{} is exist, scriptId:{}", script.getScriptVersionId(), script.getId());
                throw new ServiceException(ErrorCode.SCRIPT_VERSION_ID_EXIST);
            }
        }
        final ScriptVersionIdPair scriptVersionIdPair = new ScriptVersionIdPair();
        Long finalCreateTime = createTime;
        Long finalLastModifyTime = lastModifyTime;
        if (StringUtils.isNotBlank(script.getId())) {
            if (scriptDAO.isExistDuplicateVersion(script.getId(), script.getVersion())) {
                log.warn("Script version:{} is exist, scriptId:{}", script.getVersion(), script.getId());
                throw new ServiceException(ErrorCode.SCRIPT_VERSION_NAME_EXIST);
            }
            // 指定版本号新增
            // 不指定版本号新增
            long finalTargetAppId = targetAppId;
            dslContext.transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                if (!scriptDAO.isExistDuplicateId(appId, script.getId())) {
                    //脚本不存在，新增脚本
                    boolean isNameDuplicate = scriptDAO.isExistDuplicateName(finalTargetAppId, script.getName());
                    if (isNameDuplicate) {
                        log.warn("The script name:{} is exist for app:{}", script.getName(), finalTargetAppId);
                        throw new ServiceException(ErrorCode.SCRIPT_NAME_DUPLICATE);
                    }
                    // 插入script
                    String scriptId = scriptDAO.saveScript(context, script, finalCreateTime, finalLastModifyTime);
                    log.info("script created with specified id:{}", scriptId);
                }
                // 插入script_version
                Long scriptVersionId = scriptDAO.saveScriptVersion(context, script, finalCreateTime,
                    finalLastModifyTime);
                // 更新script
                scriptDAO.updateScript(context, script, finalLastModifyTime);
                scriptVersionIdPair.setScriptVersionId(scriptVersionId);
            });
            return Pair.of(script.getId(), scriptVersionIdPair.getScriptVersionId());
        } else {
            //脚本不存在，新增脚本
            boolean isNameDuplicate = scriptDAO.isExistDuplicateName(targetAppId, script.getName());
            if (isNameDuplicate) {
                log.warn("The script name:{} is exist for app:{}", script.getName(), targetAppId);
                throw new ServiceException(ErrorCode.SCRIPT_NAME_DUPLICATE);
            }
            script.setId(JobUUID.getUUID());
            dslContext.transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                // 插入script
                String scriptId = scriptDAO.saveScript(context, script, finalCreateTime, finalLastModifyTime);
                scriptVersionIdPair.setScriptId(scriptId);
                // 插入script_version
                Long scriptVersionId = scriptDAO.saveScriptVersion(context, script, finalCreateTime,
                    finalLastModifyTime);
                scriptVersionIdPair.setScriptVersionId(scriptVersionId);
            });
            return Pair.of(scriptVersionIdPair.getScriptId(), scriptVersionIdPair.getScriptVersionId());
        }
    }

    @Override
    public List<ScriptRelatedTaskPlanDTO> listScriptRelatedTasks(String scriptId) throws ServiceException {
        List<ScriptRelatedTaskPlanDTO> tasks = scriptRelateTaskPlanDAO.listScriptRelatedTaskPlan(scriptId);
        Map<Long, ScriptDTO> scriptVersionIdMap = new HashMap<>();
        String scriptName = "";
        if (tasks != null && !tasks.isEmpty()) {
            List<ScriptDTO> scriptVersions = scriptDAO.listByScriptId(scriptId);
            if (scriptVersions != null && !scriptVersions.isEmpty()) {
                scriptName = scriptVersions.get(0).getName();
                scriptVersions.forEach(script -> scriptVersionIdMap.put(script.getScriptVersionId(), script));
            }
            for (ScriptRelatedTaskPlanDTO task : tasks) {
                ScriptDTO theScript = scriptVersionIdMap.get(task.getScriptVersionId());
                task.setScriptName(scriptName);
                if (theScript != null) {
                    task.setScriptVersion(theScript.getVersion());
                    task.setScriptStatus(JobResourceStatusEnum.getJobResourceStatus(theScript.getStatus()));

                }
            }
        }
        return tasks;
    }

    @Override
    public List<ScriptRelatedTaskPlanDTO> listScriptVersionRelatedTasks(String scriptId, Long scriptVersionId) {
        List<ScriptRelatedTaskPlanDTO> tasks = scriptRelateTaskPlanDAO.listScriptVersionRelatedTaskPlan(scriptId,
            scriptVersionId);
        if (tasks != null && !tasks.isEmpty()) {
            ScriptDTO scriptVersion = scriptDAO.getScriptVersionById(scriptVersionId);
            for (ScriptRelatedTaskPlanDTO task : tasks) {
                task.setScriptVersion(scriptVersion.getVersion());
                task.setScriptName(scriptVersion.getName());
                task.setScriptStatus(JobResourceStatusEnum.getJobResourceStatus(scriptVersion.getStatus()));
            }
        }
        return tasks;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class, Error.class})
    public void deleteScriptVersion(String operator, Long appId, Long scriptVersionId) throws ServiceException {
        ScriptDTO existScript = scriptDAO.getScriptVersionById(scriptVersionId);
        checkDeleteScriptPermission(operator, appId, existScript);
        if (existScript.getStatus().equals(JobResourceStatusEnum.ONLINE.getValue())) {
            log.warn("Fail to delete script version because script is online. scriptVersionId={}", scriptVersionId);
            throw new ServiceException(ErrorCode.DELETE_ONLINE_SCRIPT_FAIL);
        }
        List<ScriptDTO> scriptVersions = scriptDAO.listByScriptId(existScript.getId());
        if (scriptVersions.size() == 1) {
            scriptDAO.deleteScript(existScript.getId());
            scriptDAO.deleteScriptVersion(scriptVersionId);
        } else {
            scriptDAO.deleteScriptVersion(scriptVersionId);
        }
    }

    @Override
    @Transactional(rollbackFor = {Exception.class, Error.class})
    public void deleteScript(String operator, Long appId, String scriptId) throws ServiceException {
        ScriptDTO existScript = getScript(operator, appId, scriptId);
        checkDeleteScriptPermission(operator, appId, existScript);
        scriptDAO.deleteScript(scriptId);
        scriptDAO.deleteScriptVersionByScriptId(scriptId);
    }

    private void checkDeleteScriptPermission(
        String operator,
        Long appId,
        ScriptDTO existScript
    ) throws ServiceException {
        if (existScript == null) {
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }
        long targetAppId = appId;
        if (existScript.isPublicScript()) {
            targetAppId = JobConstants.PUBLIC_APP_ID;
        }
        if (!existScript.getAppId().equals(targetAppId)) {
            throw new ServiceException(ErrorCode.SCRIPT_NOT_IN_APP);
        }
    }

    @Override
    public void publishScript(
        Long appId,
        String operator,
        String scriptId,
        Long scriptVersionId
    ) throws ServiceException {
        log.info("Begin to publish script, appId={}, operator={}, scriptId={}, scriptVersionId={}", appId, operator,
            scriptId, scriptVersionId);
        List<ScriptDTO> scriptVersions = scriptDAO.listByScriptId(scriptId);
        if (scriptVersions == null || scriptVersions.isEmpty()) {
            log.warn("Publish script, script:{} is not exist", scriptId);
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }

        boolean isPublicScript = scriptVersions.get(0).isPublicScript();
        long targetAppId = appId;
        if (isPublicScript) {
            targetAppId = JobConstants.PUBLIC_APP_ID;
        }

        boolean isScriptVersionInCurrentScript = false;
        ScriptDTO scriptVersionToBePublished = null;
        for (ScriptDTO scriptVersion : scriptVersions) {
            if (!isPublicScript && !scriptVersion.getAppId().equals(targetAppId)) {
                log.warn("Publish script, script:{} is not in current app:{}", scriptId, targetAppId);
                throw new ServiceException(ErrorCode.SCRIPT_NOT_IN_APP);
            }
            if (scriptVersion.getScriptVersionId().equals(scriptVersionId)) {
                isScriptVersionInCurrentScript = true;
                scriptVersionToBePublished = scriptVersion;
            }
        }
        if (!isScriptVersionInCurrentScript) {
            log.warn("Public script, scriptVersion:{} is not in script:{}", scriptVersionId, scriptId);
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }

        if (scriptVersionToBePublished.getStatus().equals(JobResourceStatusEnum.DISABLED.getValue())) {
            log.warn("Publish script, scriptVersion:{}, status:{} could not publish", scriptVersionId,
                scriptVersionToBePublished.getStatus());
            throw new ServiceException(ErrorCode.UNSUPPORTED_OPERATION);
        }

        ScriptDTO publishedScriptVersion = null;
        for (ScriptDTO scriptVersion : scriptVersions) {
            if (scriptVersion.getStatus().equals(JobResourceStatusEnum.ONLINE.getValue())) {
                publishedScriptVersion = scriptVersion;
                break;
            }
        }
        if (publishedScriptVersion != null) {
            log.info("Publish script, set scriptVersion:{} offline", publishedScriptVersion.getScriptVersionId());
            scriptDAO.updateScriptVersionStatus(publishedScriptVersion.getScriptVersionId(),
                JobResourceStatusEnum.OFFLINE.getValue());
            taskTemplateService.updateScriptStatus(appId, scriptId, publishedScriptVersion.getScriptVersionId(),
                JobResourceStatusEnum.OFFLINE);
        }
        scriptDAO.updateScriptVersionStatus(scriptVersionId, JobResourceStatusEnum.ONLINE.getValue());
        taskTemplateService.updateScriptStatus(appId, scriptId, scriptVersionId, JobResourceStatusEnum.ONLINE);
        log.info("Publish script successfully, scriptId={}, scriptVersionId={}", scriptId, scriptVersionId);
    }

    @Override
    public void disableScript(
        Long appId,
        String operator,
        String scriptId,
        Long scriptVersionId
    ) throws ServiceException {
        log.info("Begin to disable script, appId={}, operator={}, scriptId={}, scriptVersionId={}", appId, operator,
            scriptId, scriptVersionId);
        List<ScriptDTO> scriptVersions = scriptDAO.listByScriptId(scriptId);
        if (scriptVersions == null || scriptVersions.isEmpty()) {
            log.warn("Disable script, script:{} is not exist", scriptId);
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }

        boolean isPublicScript = scriptVersions.get(0).isPublicScript();
        long targetAppId = appId;
        if (isPublicScript) {
            targetAppId = JobConstants.PUBLIC_APP_ID;
        }

        boolean isScriptVersionInCurrentScript = false;
        ScriptDTO scriptVersionToBeDisabled = null;
        for (ScriptDTO scriptVersion : scriptVersions) {
            if (!scriptVersion.getAppId().equals(targetAppId)) {
                log.warn("Disable script, script:{} is not in current app:{}", scriptId, targetAppId);
                throw new ServiceException(ErrorCode.SCRIPT_NOT_IN_APP);
            }
            if (scriptVersion.getScriptVersionId().equals(scriptVersionId)) {
                isScriptVersionInCurrentScript = true;
                scriptVersionToBeDisabled = scriptVersion;
            }
        }
        if (!isScriptVersionInCurrentScript) {
            log.warn("Disable script, scriptVersion:{} is not in script:{}", scriptVersionId, scriptId);
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }

        if (!scriptVersionToBeDisabled.getStatus().equals(JobResourceStatusEnum.ONLINE.getValue())) {
            log.warn("Disable script, scriptVersion:{}, status:{} could not disable", scriptVersionId,
                scriptVersionToBeDisabled.getStatus());
            throw new ServiceException(ErrorCode.UNSUPPORTED_OPERATION);
        }

        scriptDAO.updateScriptVersionStatus(scriptVersionToBeDisabled.getScriptVersionId(),
            JobResourceStatusEnum.DISABLED.getValue());
        taskTemplateService.updateScriptStatus(appId, scriptId, scriptVersionToBeDisabled.getScriptVersionId(),
            JobResourceStatusEnum.DISABLED);
        log.info("Disable script successfully, scriptId={}, scriptVersionId={}", scriptId, scriptVersionId);
    }

    @Override
    public Map<String, ScriptDTO> batchGetOnlineScriptVersionByScriptIds(List<String> scriptIdList) {
        return scriptDAO.batchGetOnlineByScriptIds(scriptIdList);
    }

    @Override
    public void updateScriptDesc(Long appId, String operator, String scriptId, String desc) throws ServiceException {
        log.info("Begin to update script desc,appId={},operator={},scriptId={},desc={}", appId, operator, scriptId,
            desc);
        ScriptDTO script = scriptDAO.getScriptByScriptId(scriptId);
        checkUpdateScriptPermission(operator, appId, script);
        scriptDAO.updateScriptDesc(operator, scriptId, desc);
    }

    private void checkUpdateScriptPermission(String operator, long appId, ScriptDTO script) throws ServiceException {
        if (script == null) {
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }
        if (!script.isPublicScript()) {
            if (!script.getAppId().equals(appId)) {
                log.warn("Update script info, script:{} is not in current app:{}", script.getId(), appId);
                throw new ServiceException(ErrorCode.SCRIPT_NOT_IN_APP);
            }
        }
    }

    @Override
    public void updateScriptName(
        Long appId,
        String operator,
        String scriptId,
        String newName
    ) throws ServiceException {
        log.info("Begin to update script name,appId={},operator={},scriptId={},desc={}", appId, operator, scriptId,
            newName);
        ScriptDTO script = scriptDAO.getScriptByScriptId(scriptId);
        checkUpdateScriptPermission(operator, appId, script);
        if (script.getName().equals(newName)) {
            return;
        }
        long targetAppId = appId;
        if (script.isPublicScript()) {
            targetAppId = JobConstants.PUBLIC_APP_ID;
        }
        boolean isNameExist = scriptDAO.isExistDuplicateName(targetAppId, newName);
        if (isNameExist) {
            log.warn("Update script name, script:{} new name {} is duplicate", scriptId, newName);
            throw new ServiceException(ErrorCode.SCRIPT_NAME_DUPLICATE);
        }

        scriptDAO.updateScriptName(operator, scriptId, newName);
    }

    @Override
    public void updateScriptTags(Long appId, String operator, String scriptId, List<TagDTO> tags) {
        log.info("Begin to update script tags,appId={},operator={},scriptId={},tags={}", appId, operator, scriptId,
            tags);
        ScriptDTO script = scriptDAO.getScriptByScriptId(scriptId);
        checkUpdateScriptPermission(operator, appId, script);
        long targetAppId = appId;
        if (script.isPublicScript()) {
            targetAppId = JobConstants.PUBLIC_APP_ID;
        }
        tagService.createNewTagIfNotExist(tags, targetAppId, operator);
        scriptDAO.updateScriptTags(operator, scriptId, tags);
    }

    @Override
    public List<String> listScriptNames(Long appId, String keyword) {
        return scriptDAO.listScriptNames(appId, keyword);
    }

    @Override
    public List<ScriptDTO> listOnlineScriptForApp(String operator, long appId) {
        return scriptDAO.listOnlineScriptForApp(appId);
    }

    @Override
    public PageData<ScriptDTO> listPageOnlineScript(ScriptQueryDTO scriptCondition,
                                                    BaseSearchCondition baseSearchCondition) throws ServiceException {
        return scriptDAO.listPageOnlineScript(scriptCondition, baseSearchCondition);
    }

    @Override
    public ScriptDTO getOnlineScriptVersionByScriptId(
        String operator,
        long appId,
        String scriptId
    ) throws ServiceException {
        return scriptDAO.getOnlineByScriptId(appId, scriptId);
    }

    @Override
    public ScriptDTO getOnlineScriptVersionByScriptId(String scriptId) {
        return scriptDAO.getOnlineByScriptId(scriptId);
    }

    @Override
    public PageData<ScriptDTO> listPageScriptVersion(ScriptQueryDTO scriptQuery,
                                                     BaseSearchCondition baseSearchCondition) {
        return scriptDAO.listPageScriptVersion(scriptQuery, baseSearchCondition);
    }

    @Override
    public List<ScriptSyncTemplateStepDTO> listScriptSyncTemplateSteps(String username, Long appId, String scriptId) {
        // 检查脚本
        ScriptDTO syncScript = scriptDAO.getScriptByScriptId(scriptId);
        checkGetScriptPermission(username, appId, syncScript);

        List<ScriptSyncTemplateStepDTO> templateSteps =
            scriptRelateJobTemplateDAO.listScriptRelatedJobTemplateSteps(scriptId);
        if (CollectionUtils.isEmpty(templateSteps)) {
            return templateSteps;
        }
        // 按业务过滤
        if (appId > 0) {
            templateSteps =
                templateSteps.stream().filter(step -> step.getAppId().equals(appId)).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(templateSteps)) {
                return templateSteps;
            }
        }

        Set<Long> scriptVersionIds =
            templateSteps.stream().map(ScriptSyncTemplateStepDTO::getScriptVersionId).collect(Collectors.toSet());
        List<ScriptDTO> scriptVersions = scriptDAO.batchGetScriptVersionsByIds(scriptVersionIds);
        if (CollectionUtils.isEmpty(scriptVersions)) {
            log.warn("Scripts are not exist, scriptVersionIds: {}", scriptVersionIds);
            return Collections.emptyList();
        }

        Map<Long, ScriptDTO> scripts = new HashMap<>();
        scriptVersions.forEach(scriptVersion -> {
            scripts.put(scriptVersion.getScriptVersionId(), scriptVersion);
        });
        templateSteps.forEach(step -> fillScriptInfo(step, scripts));
        return templateSteps;
    }

    private void fillScriptInfo(ScriptSyncTemplateStepDTO templateStep, Map<Long, ScriptDTO> scripts) {
        ScriptDTO script = scripts.get(templateStep.getScriptVersionId());
        if (script != null) {
            templateStep.setScriptName(script.getName());
            templateStep.setScriptVersion(script.getVersion());
            templateStep.setScriptStatus(script.getStatus());
        }
    }

    @Override
    public List<SyncScriptResultDTO> syncScriptToTaskTemplate(String username, Long appId, String scriptId,
                                                              Long syncScriptVersionId,
                                                              List<TemplateStepIDDTO> templateStepIDs)
        throws InSufficientPermissionException {
        if (CollectionUtils.isEmpty(templateStepIDs)) {
            throw new ServiceException(ErrorCode.ILLEGAL_PARAM);
        }

        ScriptDTO syncScript = scriptDAO.getScriptVersionById(syncScriptVersionId);
        checkGetScriptPermission(username, appId, syncScript);
        if (!scriptId.equals(syncScript.getId())) {
            log.warn("Script ID and scriptVersionId not match!");
            throw new ServiceException(ErrorCode.SCRIPT_NOT_EXIST);
        }
        // 鉴权
        Set<Long> authTemplateIds = new HashSet<>();
        PermissionActionResource actionResource = new PermissionActionResource();
        actionResource.setActionId(ActionId.EDIT_JOB_TEMPLATE);
        templateStepIDs.forEach(step -> {
            TaskTemplateInfoDTO taskTemplate = taskTemplateService.getTaskTemplateBasicInfoById(step.getTemplateId());
            if (appId > 0 && taskTemplate != null && !taskTemplate.getAppId().equals(appId)) {
                throw new ServiceException(ErrorCode.TEMPLATE_NOT_EXIST);
            }
            if (taskTemplate != null) {
                step.setAppId(taskTemplate.getAppId());
                if (!authTemplateIds.contains(step.getTemplateId())) {
                    authTemplateIds.add(step.getTemplateId());
                    actionResource.addResource(ResourceTypeEnum.TEMPLATE, String.valueOf(step.getTemplateId()),
                        PathBuilder.newBuilder(ResourceTypeEnum.BUSINESS.getId(),
                            String.valueOf(taskTemplate.getAppId())).build());
                }
            }
        });
        AuthResult authResult = authService.auth(
            true,
            username,
            Collections.singletonList(actionResource)
        );
        if (!authResult.isPass()) {
            log.info("Sync script to template auth fail, scriptId: {}, syncScriptVersionId: {}, template steps: {}",
                scriptId, syncScriptVersionId, templateStepIDs);
            throw new InSufficientPermissionException(authResult);
        }

        List<SyncScriptResultDTO> syncResults = new ArrayList<>();
        templateStepIDs.forEach(templateStep -> {
            Long templateId = templateStep.getTemplateId();
            Long stepId = templateStep.getStepId();
            ScriptSyncTemplateStepDTO updateStep = new ScriptSyncTemplateStepDTO();
            updateStep.setTemplateId(templateId);
            updateStep.setStepId(stepId);
            try {
                boolean success = updateTemplateRefScript(appId, templateId, stepId, syncScriptVersionId);
                if (success) {
                    syncResults.add(SyncScriptResultDTO.buildSuccessSyncResult(updateStep));
                    refreshTemplateScriptVersionStatus(templateId);
                } else {
                    syncResults.add(SyncScriptResultDTO.buildFailSyncResult(updateStep, ErrorCode.TEMPLATE_NOT_EXIST));
                }
            } catch (Throwable e) {
                log.warn("Update script step ref script fail, templateId: {}, stepId: {}, scriptVersionId: {}",
                    templateId, stepId, syncScriptVersionId);
                syncResults.add(SyncScriptResultDTO.buildFailSyncResult(updateStep, ErrorCode.SERVICE_INTERNAL_ERROR));
            }
        });

        List<ScriptSyncTemplateStepDTO> scriptRelatedTemplateSteps = listScriptSyncTemplateSteps(username, appId,
            scriptId);
        Map<Long, ScriptSyncTemplateStepDTO> stepMap = new HashMap<>();
        scriptRelatedTemplateSteps.forEach(step -> stepMap.put(step.getStepId(), step));
        fillSyncResultDetail(syncScriptVersionId, syncResults, stepMap);

        return syncResults;
    }

    @Override
    public Integer getScriptTemplateCiteCount(String username, Long appId, String scriptId, Long scriptVersionId) {
        if (scriptVersionId == null) {
            return scriptCitedTaskTemplateDAO.countScriptCitedTaskTemplate(scriptId);
        } else {
            return scriptCitedTaskTemplateDAO.countScriptVersionCitedTaskTemplate(scriptId, scriptVersionId);
        }
    }

    @Override
    public Integer getScriptTaskPlanCiteCount(String username, Long appId, String scriptId, Long scriptVersionId) {
        if (scriptVersionId == null) {
            return scriptRelateTaskPlanDAO.countScriptRelatedTaskPlan(scriptId);
        } else {
            return scriptRelateTaskPlanDAO.countScriptVersionRelatedTaskPlan(scriptId, scriptVersionId);
        }
    }

    @Override
    public List<ScriptCitedTaskTemplateDTO> getScriptCitedTemplates(String username, Long appId, String scriptId,
                                                                    Long scriptVersionId) {
        List<ScriptCitedTaskTemplateDTO> scriptCitedTaskTemplateDTOList;
        if (scriptVersionId == null) {
            scriptCitedTaskTemplateDTOList = scriptCitedTaskTemplateDAO.listScriptCitedTaskTemplate(scriptId);
        } else {
            scriptCitedTaskTemplateDTOList = scriptCitedTaskTemplateDAO.listScriptVersionCitedTaskTemplate(scriptId,
                scriptVersionId);
        }
        //填充scriptStatusDesc
        scriptCitedTaskTemplateDTOList.forEach(scriptCitedTaskTemplateDTO -> {
            scriptCitedTaskTemplateDTO.setScriptStatusDesc(
                i18nService.getI18n(
                    scriptCitedTaskTemplateDTO.getScriptStatus().getStatusI18nKey()
                )
            );
        });
        return scriptCitedTaskTemplateDTOList;
    }

    @Override
    public List<ScriptCitedTaskPlanDTO> getScriptCitedTaskPlans(String username, Long appId, String scriptId,
                                                                Long scriptVersionId) {
        List<ScriptRelatedTaskPlanDTO> scriptRelatedTaskPlanDTOList;
        if (scriptVersionId == null) {
            scriptRelatedTaskPlanDTOList = scriptRelateTaskPlanDAO.listScriptRelatedTaskPlan(scriptId);
        } else {
            scriptRelatedTaskPlanDTOList = scriptRelateTaskPlanDAO.listScriptVersionRelatedTaskPlan(scriptId,
                scriptVersionId);
        }
        List<ScriptCitedTaskPlanDTO> scriptCitedTaskPlanDTOList = new ArrayList<>();
        if (scriptRelatedTaskPlanDTOList != null) {
            scriptRelatedTaskPlanDTOList.forEach(scriptRelatedTaskPlanDTO -> {
                ScriptCitedTaskPlanDTO scriptCitedTaskPlanDTO = new ScriptCitedTaskPlanDTO();
                scriptCitedTaskPlanDTO.setAppId(scriptRelatedTaskPlanDTO.getAppId());
                scriptCitedTaskPlanDTO.setScriptStatus(scriptRelatedTaskPlanDTO.getScriptStatus().getValue());
                scriptCitedTaskPlanDTO.setScriptVersion(scriptRelatedTaskPlanDTO.getScriptVersion());
                scriptCitedTaskPlanDTO.setScriptStatusDesc(
                    i18nService.getI18n(
                        scriptRelatedTaskPlanDTO.getScriptStatus().getStatusI18nKey()
                    )
                );
                scriptCitedTaskPlanDTO.setTaskTemplateId(scriptRelatedTaskPlanDTO.getTemplateId());
                scriptCitedTaskPlanDTO.setTaskPlanId(scriptRelatedTaskPlanDTO.getTaskId());
                scriptCitedTaskPlanDTO.setTaskPlanName(scriptRelatedTaskPlanDTO.getTaskName());
                scriptCitedTaskPlanDTOList.add(scriptCitedTaskPlanDTO);
            });
        }
        return scriptCitedTaskPlanDTOList;
    }

    private void fillSyncResultDetail(Long syncScriptVersionId, List<SyncScriptResultDTO> syncResults,
                                      Map<Long, ScriptSyncTemplateStepDTO> stepMap) {
        for (SyncScriptResultDTO syncResult : syncResults) {
            ScriptSyncTemplateStepDTO step = syncResult.getTemplateStep();
            ScriptSyncTemplateStepDTO updatedStep = stepMap.get(step.getStepId());
            if (updatedStep == null) {
                log.warn("Step is not exist, stepId: {}", step.getStepId());
                syncResult.setErrorCode(ErrorCode.TEMPLATE_NOT_EXIST);
                continue;
            }
            if (!updatedStep.getScriptVersionId().equals(syncScriptVersionId)
                && syncResult.getErrorCode() == null) {
                syncResult.setErrorCode(ErrorCode.SYNC_SCRIPT_UNKNOWN_ERROR);
            }
            step.setTemplateName(updatedStep.getTemplateName());
            step.setStepName(updatedStep.getStepName());
            step.setScriptStatus(updatedStep.getScriptStatus());
            step.setScriptVersion(updatedStep.getScriptVersion());
            step.setScriptVersionId(updatedStep.getScriptVersionId());
            step.setScriptId(updatedStep.getScriptId());
            step.setScriptName(updatedStep.getScriptName());
        }
    }

    @Transactional(rollbackFor = {Throwable.class, Error.class})
    public boolean updateTemplateRefScript(long appId, long templateId, long stepId, long syncScriptVersionId) {
        boolean success = taskScriptStepDAO.updateScriptStepRefScriptVersionId(
            templateId,
            stepId,
            syncScriptVersionId
        );
        taskTemplateDAO.updateTaskTemplateVersion(appId, templateId, UUID.randomUUID().toString());
        return success;
    }

    /*
     * 刷新模板引用脚本状态
     */
    private void refreshTemplateScriptVersionStatus(Long templateId) {
        int retry = 3;
        while (retry-- > 0) {
            try {
                templateStatusUpdateService.offerMessage(templateId);
                return;
            } catch (InterruptedException e) {
                log.error("Refresh template script version status", e);
            }
        }
    }

    @Override
    public boolean isExistAnyAppScript(Long appId) {
        return scriptDAO.isExistAnyScript(appId);
    }

    @Override
    public boolean isExistAnyPublicScript() {
        return scriptDAO.isExistAnyPublicScript();
    }

    @Override
    public Integer countScripts(Long appId, ScriptTypeEnum scriptTypeEnum,
                                JobResourceStatusEnum jobResourceStatusEnum) {
        return scriptDAO.countScripts(appId, scriptTypeEnum, jobResourceStatusEnum);
    }

    @Override
    public Integer countScriptVersions(Long appId, ScriptTypeEnum scriptTypeEnum,
                                       JobResourceStatusEnum jobResourceStatusEnum) {
        return scriptDAO.countScriptVersions(appId, scriptTypeEnum, jobResourceStatusEnum);
    }

    @Override
    public Integer countByTag(Long appId, Long tagId) {
        return scriptDAO.countByTag(appId, tagId);
    }

    @Override
    public List<String> listScriptIds(Long appId) {
        return scriptDAO.listScriptId(appId);
    }

    @Override
    public Integer countCiteScripts(Long appId) {
        // 1.查询业务下所有脚本
        List<String> scriptIdList = scriptDAO.listScriptId(appId);
        // 2.查询被引用的脚本数量
        return taskScriptStepDAO.countScriptCitedByStepsByScriptIds(appId, scriptIdList);
    }

    public void setTaskTemplateService(TaskTemplateService taskTemplateService) {
        this.taskTemplateService = taskTemplateService;
    }

    @Data
    @NoArgsConstructor
    private static class ScriptVersionIdPair {
        String scriptId;
        Long scriptVersionId;
    }
}
