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

package com.tencent.bk.job.manage.common.interceptor;

import com.tencent.bk.job.common.RequestIdLogger;
import com.tencent.bk.job.common.iam.constant.ActionId;
import com.tencent.bk.job.common.iam.exception.InSufficientPermissionException;
import com.tencent.bk.job.common.iam.model.AuthResult;
import com.tencent.bk.job.common.iam.service.AuthService;
import com.tencent.bk.job.common.util.JobContextUtil;
import com.tencent.bk.job.common.util.SimpleRequestIdLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * Uri权限控制拦截
 */
@Slf4j
@Component
public class UriPermissionInterceptor extends HandlerInterceptorAdapter {
    private static final RequestIdLogger logger =
        new SimpleRequestIdLogger(LoggerFactory.getLogger(UriPermissionInterceptor.class));
    private final String URI_PATTERN_WHITE_IP = "/web/whiteIP/**";
    private final String URI_PATTERN_NOTIFY_BLACKLIST = "/web/notify/users/blacklist";
    private final String URI_PATTERN_GLOBAL_SETTINGS = "/web/globalSettings/**";
    private final String URI_PATTERN_PUBLIC_SCRIPT = "/web/public_script/**";
    private final String URI_PATTERN_PUBLIC_TAG = "/web/public_tag/**";
    private final String URI_PATTERN_SERVICE_INFO = "/web/serviceInfo/**";
    private final String URI_PATTERN_DANGEROUS_RULE = "/web/dangerousRule/**";
    private AuthService authService;
    private PathMatcher pathMatcher;

    @Autowired
    public UriPermissionInterceptor(AuthService authService) {
        this.authService = authService;
        this.pathMatcher = new AntPathMatcher();
    }

    public String[] getControlUriPatterns() {
        Object[] rawArr = getControlUriPatternsList().toArray();
        String[] arr = new String[rawArr.length];
        for (int i = 0; i < rawArr.length; i++) {
            arr[i] = (String) rawArr[i];
        }
        return arr;
    }

    private List<String> getControlUriPatternsList() {
        return Arrays.asList(
            //IP白名单
            URI_PATTERN_WHITE_IP,
            //通知黑名单
            URI_PATTERN_NOTIFY_BLACKLIST,
            //全局设置
            URI_PATTERN_GLOBAL_SETTINGS,
            // 公共脚本
            URI_PATTERN_PUBLIC_SCRIPT,
            // 公共标签
            URI_PATTERN_PUBLIC_TAG,
            // 服务状态
            URI_PATTERN_SERVICE_INFO,
            // 高危语句规则
            URI_PATTERN_DANGEROUS_RULE
        );
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        String username = JobContextUtil.getUsername();
        String uri = request.getRequestURI();
        logger.infoWithRequestId("PermissionControlInterceptor.preHandle:username=" + username + ", uri=" + uri + ", " +
            "controlUriPatterns=" + getControlUriPatternsList());
        //仅超级管理员可使用管理相关接口
        if (pathMatcher.match(URI_PATTERN_NOTIFY_BLACKLIST, uri)) {
            AuthResult authResult = authService.auth(true, username, ActionId.GLOBAL_SETTINGS);
            if (!authResult.isPass()) {
                throw new InSufficientPermissionException(authResult);
            }
        } else if (pathMatcher.match(URI_PATTERN_GLOBAL_SETTINGS, uri)) {
            AuthResult authResult = authService.auth(true, username, ActionId.GLOBAL_SETTINGS);
            if (!authResult.isPass()) {
                throw new InSufficientPermissionException(authResult);
            }
        } else if (pathMatcher.match(URI_PATTERN_SERVICE_INFO, uri)) {
            AuthResult authResult = authService.auth(true, username, ActionId.SERVICE_STATE_ACCESS);
            if (!authResult.isPass()) {
                throw new InSufficientPermissionException(authResult);
            }
        } else if (pathMatcher.match(URI_PATTERN_DANGEROUS_RULE, uri)) {
            AuthResult authResult = authService.auth(true, username, ActionId.HIGH_RISK_DETECT_RULE);
            if (!authResult.isPass()) {
                throw new InSufficientPermissionException(authResult);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
        throws Exception {

    }
}
