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

package com.tencent.bk.job.manage.dao.template.impl;

import com.tencent.bk.job.common.constant.ErrorCode;
import com.tencent.bk.job.common.exception.ServiceException;
import com.tencent.bk.job.manage.common.consts.task.TaskTypeEnum;
import com.tencent.bk.job.manage.common.util.DbRecordMapper;
import com.tencent.bk.job.manage.common.util.JooqDataTypeUtil;
import com.tencent.bk.job.manage.dao.TaskVariableDAO;
import com.tencent.bk.job.manage.model.dto.task.TaskVariableDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jooq.*;
import org.jooq.generated.tables.TaskTemplateVariable;
import org.jooq.generated.tables.records.TaskTemplateVariableRecord;
import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @since 3/10/2019 21:54
 */
@Slf4j
@Repository("TaskTemplateVariableDAOImpl")
public class TaskTemplateVariableDAOImpl implements TaskVariableDAO {

    private static final TaskTemplateVariable TABLE = TaskTemplateVariable.TASK_TEMPLATE_VARIABLE;

    private DSLContext context;

    @Autowired
    public TaskTemplateVariableDAOImpl(@Qualifier("job-manage-dsl-context") DSLContext context) {
        this.context = context;
    }

    @Override
    public List<TaskVariableDTO> listVariablesByParentId(long parentId) {

        List<Condition> conditions = new ArrayList<>();
        conditions.add(TABLE.TEMPLATE_ID.eq(ULong.valueOf(parentId)));

        Result<
            Record8<ULong, ULong, String, UByte, String, String, UByte,
                UByte>> records =
            context
                .select(TABLE.ID, TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE, TABLE.DEFAULT_VALUE,
                    TABLE.DESCRIPTION, TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED)
                .from(TABLE).where(conditions).fetch();

        List<TaskVariableDTO> taskVariableList = new ArrayList<>();

        if (records != null && records.size() >= 1) {
            records.forEach(record -> taskVariableList
                .add(DbRecordMapper.convertRecordToTaskVariable(record, TaskTypeEnum.TEMPLATE)));
        }
        return taskVariableList;
    }

    @Override
    public TaskVariableDTO getVariableById(long parentId, long id) {
        List<Condition> conditions = new ArrayList<>(2);
        conditions.add(TABLE.ID.eq(ULong.valueOf(id)));
        conditions.add(TABLE.TEMPLATE_ID.eq(ULong.valueOf(parentId)));
        Record8<ULong, ULong, String, UByte, String, String, UByte, UByte> record =
            context.select(TABLE.ID, TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE, TABLE.DEFAULT_VALUE, TABLE.DESCRIPTION,
                TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED).from(TABLE).where(conditions).fetchOne();
        if (record != null) {
            return DbRecordMapper.convertRecordToTaskVariable(record, TaskTypeEnum.TEMPLATE);
        } else {
            return null;
        }
    }

    @Override
    public TaskVariableDTO getVariableByName(long parentId, String name) {
        List<Condition> conditions = new ArrayList<>(2);
        conditions.add(TABLE.NAME.eq(name));
        conditions.add(TABLE.TEMPLATE_ID.eq(ULong.valueOf(parentId)));
        Result<Record8<ULong, ULong, String, UByte, String, String, UByte, UByte>> records =
            context.select(TABLE.ID, TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE, TABLE.DEFAULT_VALUE, TABLE.DESCRIPTION,
                TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED).from(TABLE).where(conditions).fetch();
        if (records != null && records.size() > 0) {
            return DbRecordMapper.convertRecordToTaskVariable(records.get(0), TaskTypeEnum.TEMPLATE);
        } else {
            return null;
        }
    }

    @Override
    public long insertVariable(TaskVariableDTO variable) {
        TaskTemplateVariableRecord record = context.insertInto(TABLE)
            .columns(TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE, TABLE.DEFAULT_VALUE, TABLE.DESCRIPTION,
                TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED)
            .values(ULong.valueOf(variable.getTemplateId()), variable.getName(),
                UByte.valueOf(variable.getType().getType()), variable.getDefaultValue(), variable.getDescription(),
                getChangeable(variable.getChangeable()), getRequired(variable.getRequired()))
            .returning(TABLE.ID).fetchOne();
        if (record != null) {
            return record.getId().longValue();
        } else {
            return 0;
        }
    }

    @Override
    public List<Long> batchInsertVariables(List<TaskVariableDTO> variableList) {
        if (CollectionUtils.isEmpty(variableList)) {
            return Collections.emptyList();
        }
        InsertValuesStep7<TaskTemplateVariableRecord, ULong, String, UByte, String, String, UByte, UByte> insertStep =
            context.insertInto(TABLE).columns(TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE, TABLE.DEFAULT_VALUE,
                TABLE.DESCRIPTION, TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED);

        variableList.forEach(variable -> insertStep.values(ULong.valueOf(variable.getTemplateId()), variable.getName(),
            UByte.valueOf(variable.getType().getType()), variable.getDefaultValue(), variable.getDescription(),
            getChangeable(variable.getChangeable()), getRequired(variable.getRequired())));

        Result<TaskTemplateVariableRecord> result = insertStep.returning(TABLE.ID).fetch();
        List<Long> variableIdList = new ArrayList<>(variableList.size());
        result.forEach(record -> variableIdList.add(record.getId().longValue()));

        try {
            Iterator<TaskVariableDTO> variableIterator = variableList.iterator();
            Iterator<Long> variableIdIterator = variableIdList.iterator();
            while (variableIterator.hasNext()) {
                TaskVariableDTO taskFileInfo = variableIterator.next();
                taskFileInfo.setId(variableIdIterator.next());
            }
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.BATCH_INSERT_FAILED);
        }

        return variableIdList;
    }

    @Override
    public boolean updateVariableById(TaskVariableDTO variable) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(TABLE.ID.eq(ULong.valueOf(variable.getId())));
        conditions.add(TABLE.TEMPLATE_ID.eq(ULong.valueOf(variable.getTemplateId())));

        UByte isChangeable = UByte.valueOf(0);
        UByte isRequired = UByte.valueOf(0);
        if (variable.getChangeable() != null && variable.getChangeable()) {
            isChangeable = UByte.valueOf(1);
        }
        if (variable.getRequired() != null && variable.getRequired()) {
            isRequired = UByte.valueOf(1);
        }

        if (variable.getType().isNeedMask() && variable.getDefaultValue().equals(variable.getType().getMask())) {
            return 1 == context.update(TABLE).set(TABLE.NAME, variable.getName())
                .set(TABLE.DESCRIPTION, variable.getDescription()).set(TABLE.IS_CHANGEABLE, isChangeable)
                .set(TABLE.IS_REQUIRED, isRequired).where(conditions).limit(1).execute();
        }

        return 1 == context.update(TABLE).set(TABLE.NAME, variable.getName())
            .set(TABLE.DESCRIPTION, variable.getDescription()).set(TABLE.DEFAULT_VALUE, variable.getDefaultValue())
            .set(TABLE.IS_CHANGEABLE, isChangeable).set(TABLE.IS_REQUIRED, isRequired).where(conditions).limit(1)
            .execute();
    }

    @Override
    public boolean deleteVariableById(long parentId, long id) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(TABLE.ID.eq(ULong.valueOf(id)));
        conditions.add(TABLE.TEMPLATE_ID.eq(ULong.valueOf(parentId)));
        return 1 == context.deleteFrom(TABLE).where(conditions).limit(1).execute();
    }

    @Override
    public boolean batchInsertVariableWithId(List<TaskVariableDTO> variableList) {
        InsertValuesStep8<TaskTemplateVariableRecord, ULong, ULong, String, UByte, String, String, UByte,
            UByte> insertStep = context.insertInto(TABLE).columns(TABLE.ID, TABLE.TEMPLATE_ID, TABLE.NAME, TABLE.TYPE,
            TABLE.DEFAULT_VALUE, TABLE.DESCRIPTION, TABLE.IS_CHANGEABLE, TABLE.IS_REQUIRED);
        for (TaskVariableDTO variable : variableList) {
            insertStep = insertStep.values(JooqDataTypeUtil.buildULong(variable.getId()),
                ULong.valueOf(variable.getTemplateId()), variable.getName(),
                UByte.valueOf(variable.getType().getType()), variable.getDefaultValue(), variable.getDescription(),
                getChangeable(variable.getChangeable()), getRequired(variable.getRequired()));
        }
        return insertStep.execute() > 0;
    }

    @Override
    public boolean updateVariableByName(TaskVariableDTO variable) {
        throw new UnsupportedOperationException();
    }

    private UByte getChangeable(Boolean changeable) {
        UByte isChangeable = UByte.valueOf(0);
        if (changeable != null && changeable) {
            isChangeable = UByte.valueOf(1);
        }
        return isChangeable;
    }

    private UByte getRequired(Boolean required) {
        UByte isRequired = UByte.valueOf(0);
        if (required != null && required) {
            isRequired = UByte.valueOf(1);
        }
        return isRequired;
    }
}
