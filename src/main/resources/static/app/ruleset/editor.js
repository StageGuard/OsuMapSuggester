// noinspection JSUnusedGlobalSymbols
let lastSubmitted = {
    name: "",
    triggers: "",
    expression: ""
};

mainApp.component("ruleset-editor", {
    template: `
        <!-- modal -->
        <div
            class="modal fade"
            id="deleteConfirm"
            data-mdb-backdrop="static"
            data-mdb-keyboard="false"
            tabindex="-1"
            aria-labelledby="deleteConfirmLabel"
            aria-hidden="true"
        >
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="deleteConfirmLabel">删除谱面类型规则</h5>
                    </div>
                    <div class="modal-body">确认要删除 {{ ruleset.name }} 谱面类型规则吗？<br/><b>该操作不可撤销！</b></div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-mdb-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-primary" data-mdb-dismiss="modal" @click="deleteRuleset">确认</button>
                    </div>
                </div>
            </div>
        </div>
        <div class="row gx-sm-5 gy-sm-3" v-show="show">
            <h2 class="mainTitle"><b>{{ mainTitle }}</b></h2>
            <h4 class="mainTitle" v-html="subTitle"></h4>
            <!-- form area -->
            <div class="col-sm-5" v-show="showEditor">
                <form class="card bg-light" @submit.prevent="submitRuleset">
                    <div class="card-header"><b>谱面规则信息</b></div>
                    <div class="card-body">
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则名称</b><br><small>为你的规则设置名称，用于显示在推图结果中。</small>
                            </h6>
                            <input type="text" class="form-control form-control-lg" v-model="ruleset.name"/>
                        </div>
                        
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则触发词</b><br><small>为你的规则设置触发词，用于触发你的规则。<br/>允许正则表达式，不允许空格，使用英文分号分隔多个触发词。</small>
                            </h6>
                            <input type="text" class="form-control form-control-lg" v-model="ruleset.triggers"/>
                        </div>
                        
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则表达式</b><br><small>为你的规则设置 JavaScript 匹配表达式。<br/>访问 <a href="https://github.com/StageGuard/OsuMapSuggester/wiki/Beatmap-Ruleset-Expression" target="_blank">Beatmap Ruleset Expression</a> 获取更多信息。</small>
                            </h6>
                            <textarea type="text" class="form-control form-control-lg" v-model="ruleset.expression" @blur="checkExpressionSyntax"/>
                        </div>
                        
                        <div class="formItem card" v-show="expressionSyntax.hasSyntaxError">
                            <div class="card-header"><b>语法检查结果</b></div>
                            <div v-for="err in expressionSyntax.message">
                                <div class="alert" :class="{
                                    'alert-danger': err.startsWith('ERROR:'), 
                                    'alert-warning': err.startsWith('WARNING:') 
                                }" style="margin: 0; border-radius: 0">
                                    <i class="fa" :class="{
                                        'fa-times-circle': err.startsWith('ERROR:'), 
                                        'fa-exclamation-triangle': err.startsWith('WARNING:') 
                                   }" style="border-radius: 0"></i> {{ err }}
                                </div>
                            </div>
                            <div class="card-footer">请检查表达式，有 <code>ERROR</code> 将无法保存。</div>
                        </div>
                    </div>

                    <div class="card-footer">
                        <button 
                            type="submit"
                            class="btn btn-primary" 
                            style="float: right; margin: 3px"
                            :disabled="!submitClickable"
                        >保存</button>
                        <button
                            type="button"
                            class="btn btn-primary"
                            style="float: right; margin: 3px"
                            v-show="deleteShow"
                            data-mdb-toggle="modal"
                            data-mdb-target="#deleteConfirm"
                        >删除</button>
                    </div>
                </form>
            </div>
            <div class="col-sm-7" v-show="showEditor">
                这里将展示匹配结果，但是还没做好。
            </div>
        </div>`,

    props: {
        show: {
            type: Boolean,
            required: true
        },
        qq: {
            type: Number,
            required: true
        }
    },

    emits: ["error-broadcast", "warning-broadcast", "success-broadcast"],

    data() {
        return {
            mainTitle: "权限检查中",
            subTitle: "很快就好。",

            showEditor: false,

            ruleset: {
                name: "",
                triggers: "",
                expression: ""
            },

            lastCheckedExpression: "",

            expressionSyntax: {
                hasSyntaxError: false,
                message: [],
            }
        }
    },

    watch: {
        show(newValue, _) {
            if (newValue === true) this.checkAccess();
        },
    },

    created() {
        if(getCookie("new_redirect") === "true") {
            this.$emit("success-broadcast", "保存成功。");
            document.cookie = "new_redirect=false";
        } else if(getCookie("delete_direct") === "true") {
            this.$emit("success-broadcast", "删除成功。");
            document.cookie = "delete_direct=false";
        }
    },

    computed: {
        submitClickable() {
            if(this.ruleset.name === "" || this.ruleset.triggers === "" || this.ruleset.expression === "") {
                return false;
            }
            if(!this.modifiedRulesetName && !this.modifiedRulesetTriggers && !this.modifiedRulesetExpression) {
                return false;
            }
            return !this.expressionSyntax.hasSyntaxError;
        },
        deleteShow() {
            return !document.location.pathname.includes("new");
        },
        modifiedRulesetName() { return lastSubmitted.name !== this.ruleset.name; },
        modifiedRulesetTriggers() { return lastSubmitted.triggers !== this.ruleset.triggers; },
        modifiedRulesetExpression() { return lastSubmitted.expression !== this.ruleset.expression; },
    },

    methods: {
        async submitRuleset() {
            const appRoot = this;
            let token = getCookie("token");
            if(!token) {
                appRoot.$emit("error-broadcast", "认证失效，请刷新界面重新认证（编辑内容将丢失）。", 4000);
                return;
            }

            if(!appRoot.submitClickable) {
                appRoot.$emit("error-broadcast", "参数不合法，请检查各项填写是否有误。", 4000);
                return;
            }

            let editType = (path => {
                let sp = path.split("/");
                return sp[sp.length - 1];
            })(document.location.pathname);

            (await fetch("/ruleset/submit", {
                method: 'POST',
                body: JSON.stringify({
                    "token": token,
                    "type": 0,
                    "ruleset": {
                        "id": editType === "new" ? 0 : Number(editType),
                        "name": appRoot.ruleset.name,
                        "triggers": appRoot.ruleset.triggers.split(";"),
                        "expression": appRoot.ruleset.expression
                    }
                }),
            })).json().then(submitResult => {
                appRoot.processSubmitResult(submitResult, () => {
                    if(submitResult.newId !== 0 && editType === "new") {
                        document.cookie = "new_redirect=true";
                        window.location.href = "/ruleset/edit/" + submitResult.newId
                    } else {
                        appRoot.$emit("success-broadcast", "保存成功。");
                        lastSubmitted.name = appRoot.ruleset.name;
                        lastSubmitted.triggers = appRoot.ruleset.triggers;
                        lastSubmitted.expression = appRoot.ruleset.expression;
                    }
                });
            });
        },

        async deleteRuleset() {
            const appRoot = this;
            let token = getCookie("token");
            if(!token) {
                appRoot.$emit("error-broadcast", "认证失效，请刷新界面重新认证（编辑内容将丢失）。", 4000);
                return;
            }

            let editType = (path => {
                let sp = path.split("/");
                return sp[sp.length - 1];
            })(document.location.pathname);

            if(editType === "new") {
                appRoot.$emit("error-broadcast", "无法找到这个谱面类型规则，可能已经被删除。", 4000);
                return;
            }

            (await fetch("/ruleset/submit", {
                method: 'POST',
                body: JSON.stringify({
                    "token": token,
                    "type": 1,
                    "ruleset": { "id": Number(editType) }
                }),
            })).json().then(submitResult => {
                appRoot.processSubmitResult(submitResult, () => {
                    document.cookie = "delete_direct=true";
                    window.location.href = "/ruleset/edit/new";
                });
            });
        },

        processSubmitResult(submitResult, onSuccess) {
            const appRoot = this;
            switch (Number(submitResult.result)) {
                case 0: case 5: onSuccess(); break;
                case 1: appRoot.$emit("error-broadcast", "认证失效，请刷新界面重新认证（编辑内容将丢失）。", 4000); break;
                case 2: appRoot.$emit("error-broadcast", "权限拒绝，你没有权限操作这个谱面类型规则。", 4000); break;
                case 3: appRoot.$emit("error-broadcast", "参数不合法，请检查各项填写是否有误。", 4000); break;
                case 4: appRoot.$emit("error-broadcast", "无法找到这个谱面类型规则，可能已经被删除。", 4000); break;
                case 6: appRoot.$emit("warning-broadcast", "未知的操作。", 4000); break;
                case -1: {
                    appRoot.$emit("error-broadcast", "内部错误：" + submitResult.errorMessage + "<br/>请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。", 10000);
                    break;
                }
            }
        },

        async checkAccess() {
            const appRoot = this;

            let editType = (path => {
                let sp = path.split("/");
                return sp[sp.length - 1];
            })(document.location.pathname);

            (await fetch("/ruleset/checkAccess", {
                method: 'POST',
                body: JSON.stringify({
                    "qq": appRoot.qq,
                    "editType": editType === "new" ? 1 : 2,
                    "rulesetId": editType === "new" ? 0 : Number(editType)
                }),
            })).json().then(checkResponse => {
                switch (Number(checkResponse.result)) {
                    case 0: {
                        if (Number(checkResponse.ruleset.id) === 0) {
                            appRoot.mainTitle = "添加谱面类型规则";
                            appRoot.subTitle = "请确保熟悉了谱面类型规则后再进行添加。<br/>";
                            appRoot.subTitle += '访问 <a href="https://github.com/StageGuard/OsuMapSuggester/wiki/Beatmap-Type-Ruleset" target="_blank">Beatmap Type Ruleset<a/> 获取更多信息。';
                        } else {
                            appRoot.ruleset.name = lastSubmitted.name = checkResponse.ruleset.name;
                            appRoot.ruleset.triggers = lastSubmitted.triggers = checkResponse.ruleset.triggers.join(";");
                            appRoot.ruleset.expression = lastSubmitted.expression = checkResponse.ruleset.expression;

                            appRoot.mainTitle = "编辑谱面类型规则";
                            appRoot.subTitle = "<b>" + appRoot.ruleset.name + "</b> by QQ: <b>" + appRoot.qq + "</b>";
                        }
                        appRoot.showEditor = true;
                        break;
                    }
                    case 1: {
                        appRoot.mainTitle = "谱面类型规则未找到";
                        appRoot.subTitle = "未找到 ID 为 " + editType + " 的谱面类型规则，请确保 ID 正确。";
                        break;
                    }
                    case 2: {
                        appRoot.mainTitle = "无法编辑谱面类型规则";
                        appRoot.subTitle = "你不是谱面类型规则 <b>" + checkResponse.ruleset.name + "</b> 的创建者，无法编辑。";
                        break;
                    }
                    case -1: {
                        appRoot.mainTitle = "内部错误";
                        appRoot.subTitle = "发生了内部错误<br/>";
                        appRoot.subTitle += "请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。";

                        appRoot.$emit("error-broadcast", checkResponse.errorMessage)
                        break;
                    }
                }
            })
        },

        async checkExpressionSyntax() {
            const appRoot = this;

            if(appRoot.lastCheckedExpression !== appRoot.ruleset.expression) {
                appRoot.expressionSyntax.hasSyntaxError = false;
                appRoot.expressionSyntax.message = [];

                (await fetch("/ruleset/checkSyntax", {
                    method: 'POST',
                    body: JSON.stringify({"code": appRoot.ruleset.expression}),
                })).json().then(checkResponse => {
                    if (Number(checkResponse.result) === 0) {
                        appRoot.expressionSyntax.hasSyntaxError = checkResponse.message.length !== 0;
                        appRoot.expressionSyntax.message = checkResponse.message;
                    } else {
                        appRoot.expressionSyntax.hasSyntaxError = true;
                        appRoot.expressionSyntax.message.push("ERROR: Internal error: " + checkResponse.errorMessage);
                        appRoot.$emit("error-broadcast", checkResponse.errorMessage);
                    }
                    appRoot.lastCheckedExpression = appRoot.ruleset.expression;
                });
            }
        }
    }
})