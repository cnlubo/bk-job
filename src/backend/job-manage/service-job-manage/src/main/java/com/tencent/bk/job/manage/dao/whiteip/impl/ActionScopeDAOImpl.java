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

package com.tencent.bk.job.manage.dao.whiteip.impl;

import com.tencent.bk.job.common.i18n.service.MessageI18nService;
import com.tencent.bk.job.manage.common.consts.whiteip.ActionScopeEnum;
import com.tencent.bk.job.manage.dao.whiteip.ActionScopeDAO;
import com.tencent.bk.job.manage.model.dto.whiteip.ActionScopeDTO;
import com.tencent.bk.job.manage.model.web.vo.whiteip.ActionScopeVO;
import lombok.val;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.generated.tables.ActionScope;
import org.jooq.generated.tables.records.ActionScopeRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ActionScopeDAOImpl implements ActionScopeDAO {
    private DSLContext defaultDslContext;
    private static final ActionScope T_ACTION_SCOPE = ActionScope.ACTION_SCOPE;
    private final MessageI18nService i18nService;

    @Autowired
    public ActionScopeDAOImpl(DSLContext dslContext, MessageI18nService i18nService) {
        this.defaultDslContext = dslContext;
        this.i18nService = i18nService;
    }

    @Override
    public Long insertActionScope(DSLContext dslContext, ActionScopeDTO actionScopeDTO) {
        Record record = dslContext.insertInto(T_ACTION_SCOPE,
            T_ACTION_SCOPE.NAME,
            T_ACTION_SCOPE.DESCRIPTION,
            T_ACTION_SCOPE.CREATOR,
            T_ACTION_SCOPE.CREATE_TIME,
            T_ACTION_SCOPE.LAST_MODIFY_USER,
            T_ACTION_SCOPE.LAST_MODIFY_TIME
        ).values(
            actionScopeDTO.getName(),
            actionScopeDTO.getDescription(),
            actionScopeDTO.getCreator(),
            ULong.valueOf(actionScopeDTO.getCreateTime()),
            actionScopeDTO.getLastModifier(),
            ULong.valueOf(actionScopeDTO.getLastModifyTime())
        ).returning(T_ACTION_SCOPE.ID)
            .fetchOne();
        return record.get(T_ACTION_SCOPE.ID);
    }

    @Override
    public int deleteActionScopeById(DSLContext dslContext, Long id) {
        return dslContext.deleteFrom(T_ACTION_SCOPE).where(
            T_ACTION_SCOPE.ID.eq(id)
        ).execute();
    }

    @Override
    public ActionScopeDTO getActionScopeById(Long id) {
        ActionScopeRecord record = defaultDslContext.selectFrom(T_ACTION_SCOPE).where(
            T_ACTION_SCOPE.ID.eq(id)
        ).fetchOne();
        return convert(record);
    }

    @Override
    public ActionScopeDTO getActionScopeByCode(String code) {
        ActionScopeRecord record = defaultDslContext.selectFrom(T_ACTION_SCOPE).where(
            T_ACTION_SCOPE.CODE.eq(code)
        ).fetchOne();
        return convert(record);
    }

    @Override
    public ActionScopeVO getActionScopeVOById(Long id) {
        val record = defaultDslContext.selectFrom(T_ACTION_SCOPE).where(
            T_ACTION_SCOPE.ID.eq(id)
        ).fetchOne();
        if (record == null) {
            return null;
        } else {
            return new ActionScopeVO(
                record.getId(),
                i18nService.getI18n(ActionScopeEnum.getI18nCodeByName(record.getCode())),
                record.getCreator(),
                record.getCreateTime().longValue(),
                record.getLastModifyUser(),
                record.getLastModifyTime().longValue()
            );
        }
    }

    @Override
    public List<ActionScopeDTO> listActionScopeDTO() {
        val records = defaultDslContext.selectFrom(T_ACTION_SCOPE).fetch();
        if (records == null) {
            return new ArrayList<>();
        }
        return records.stream().map(record ->
            new ActionScopeDTO(
                record.getId(),
                record.getCode(),
                record.getName(),
                record.getDescription(),
                record.getCreator(),
                record.getCreateTime().longValue(),
                record.getLastModifyUser(),
                record.getLastModifyTime().longValue()
            )
        ).collect(Collectors.toList());
    }

    @Override
    public int updateActionScopeById(DSLContext dslContext, ActionScopeDTO actionScopeDTO) {
        return dslContext.update(T_ACTION_SCOPE)
            .set(T_ACTION_SCOPE.NAME, actionScopeDTO.getName())
            .set(T_ACTION_SCOPE.DESCRIPTION, actionScopeDTO.getDescription())
            .set(T_ACTION_SCOPE.CREATOR, actionScopeDTO.getCreator())
            .set(T_ACTION_SCOPE.CREATE_TIME, ULong.valueOf(actionScopeDTO.getCreateTime()))
            .set(T_ACTION_SCOPE.LAST_MODIFY_USER, actionScopeDTO.getLastModifier())
            .set(T_ACTION_SCOPE.LAST_MODIFY_TIME, ULong.valueOf(actionScopeDTO.getLastModifyTime()))
            .where(T_ACTION_SCOPE.ID.eq(actionScopeDTO.getId()))
            .execute();
    }

    private ActionScopeDTO convert(ActionScopeRecord record) {
        if (record == null) return null;
        return new ActionScopeDTO(
            record.getId(),
            record.getCode(),
            record.getName(),
            record.getDescription(),
            record.getCreator(),
            record.getCreateTime().longValue(),
            record.getLastModifyUser(),
            record.getLastModifyTime().longValue()
        );
    }
}
