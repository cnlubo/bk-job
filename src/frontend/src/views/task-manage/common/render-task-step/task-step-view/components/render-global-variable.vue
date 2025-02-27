<!--
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
-->

<template>
    <div class="step-view-global-variable" @click="handlerView">
        <div class="flag">
            <Icon type="host" />
        </div>
        <div class="name" :title="name">{{ name }}</div>
        <bk-dialog
            v-model="isShowDetail"
            :title="title"
            :width="1020"
            :ok-text="$t('template.关闭')"
            class="global-host-variable-detail-dialog">
            <template #header>
                <div class="variable-title">
                    <span>{{ title }}</span>
                    <i class="global-variable-dialog-close bk-icon icon-close" @click="handleClose" />
                </div>
            </template>
            <div class="content-wraper">
                <Empty v-if="isEmpty" :title="$t('template.变量值为空')" style="height: 100%;" />
                <scroll-faker v-else>
                    <server-panel
                        detail-mode="dialog"
                        :host-node-info="hostNodeInfo" />
                </scroll-faker>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import I18n from '@/i18n';
    import TaskHostNodeModel from '@model/task-host-node';
    import ScrollFaker from '@components/scroll-faker';
    import ServerPanel from '@components/choose-ip/server-panel';
    import Empty from '@components/empty';

    export default {
        name: 'StepViewGlobalVariable',
        components: {
            ScrollFaker,
            ServerPanel,
            Empty,
        },
        props: {
            type: {
                type: String,
                default: '',
            },
            name: {
                type: String,
                required: true,
            },
            data: {
                type: Array,
                default: () => [],
            },
        },
        data () {
            const { hostNodeInfo } = new TaskHostNodeModel({});
            
            return {
                isShowDetail: false,
                hostNodeInfo,
            };
        },
        computed: {
            title () {
                if (this.type) {
                    return this.type;
                }
                return `${I18n.t('template.全局变量.label')} - ${this.name}`;
            },
            isEmpty () {
                return TaskHostNodeModel.isHostNodeInfoEmpty(this.hostNodeInfo);
            },
        },
        methods: {
            handlerView () {
                const curVariable = this.data.find(item => item.name === this.name);
                this.hostNodeInfo = Object.freeze(curVariable.defaultTargetValue.hostNodeInfo);
                
                this.isShowDetail = true;
            },
            handleClose () {
                this.isShowDetail = false;
            },
        },
    };
</script>
<style lang="postcss">
    .global-host-variable-detail-dialog {
        .bk-dialog-tool {
            display: none;
        }

        .bk-dialog-header,
        .bk-dialog-footer {
            position: relative;
            z-index: 99999;
            background: #fff;
        }

        .bk-dialog-header {
            padding: 0;
        }

        .bk-dialog-wrapper .bk-dialog-header .bk-dialog-header-inner {
            font-size: 20px;
            color: #000;
            text-align: left;
        }

        .bk-dialog-wrapper .bk-dialog-body {
            padding: 0;

            .server-panel {
                height: 100%;

                &.show-detail {
                    overflow: hidden;
                }

                .host-detail.show {
                    padding-left: 20%;
                }
            }
        }

        .content-wraper {
            height: 450px;
            max-height: 450px;
            min-height: 450px;
            margin-top: -1px;
        }

        button[name="cancel"] {
            display: none;
        }

        .variable-title {
            position: relative;
            height: 68px;
            padding-top: 0;
            padding-bottom: 0;
            padding-left: 25px;
            font-size: 20px;
            line-height: 68px;
            color: #000;
            text-align: left;
            border-bottom: 1px solid #dcdee5;
        }

        .global-variable-dialog-close {
            position: absolute;
            top: 0;
            right: 0;
            font-size: 32px;
            color: #c4c6cc;
            cursor: pointer;
            transition: all 0.15s;

            &:hover {
                transform: rotateZ(90deg);
            }
        }
    }
</style>
<style lang='postcss' scoped>
    .step-view-global-variable {
        display: inline-flex;
        height: 24px;
        padding-right: 10px;
        line-height: 1;
        cursor: pointer;
        background: #fff;

        .flag {
            display: flex;
            height: 24px;
            font-size: 13px;
            color: #fff;
            background: #3a84ff;
            border-bottom-left-radius: 2px;
            border-top-left-radius: 2px;
            flex: 0 0 24px;
            align-items: center;
            justify-content: center;
        }

        .name {
            display: flex;
            padding: 0 10px;
            white-space: nowrap;
            border: 1px solid #dcdee5;
            border-left: none;
            border-top-right-radius: 2px;
            border-bottom-right-radius: 2px;
            align-items: center;
        }
    }
</style>
