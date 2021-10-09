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
                                    <i class="me-3 fa" :class="{
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
            <div class="col-sm-7">
                <div class="card bg-light">
                    <div class="card-header"><b><code>contains</code> 匹配结果</b></div>
                    <div v-if="editType === 'new'" class="alert alert-primary" style="margin: 0; border-radius: 0">
                        <i class="fa fa-info-circle me-2" style="border-radius: 0"></i>新建谱面规则时无法编辑谱面备注。<br/>将在保存谱面规则后显示匹配谱面。
                    </div>
                    <div v-else-if="containsExprMatched.comments.length == 0" class="alert alert-primary" style="margin: 0; border-radius: 0">
                        <i class="fa fa-info-circle me-2" style="border-radius: 0"></i>表达式中没有 <code>contains</code> 表达式。
                    </div>
                    <div v-else v-for="comment in containsExprMatched.comments">
                        <div class="alert alert-light" style="margin: 0; border-radius: 0">
                            <div style="display: inline;">
                                <label for="basic-url" class="form-label">BID: </label><span class="text-info me-3">{{ comment.bid }}</span>
                                <div v-if="containsExprMatched.cachedBeatmapInfo[String(comment.bid)] != null" v-html="getBeatmapInfo(comment.bid)"></div>
                            </div>
                            <input type="text" class="form-control form-control-lg" v-model="comment.comment" :disabled="modifiedRulesetExpression" @input="containsExprMatched.edited = true;"/>
                        </div>
                    </div>
                    <div class="card-footer">
                        <button
                            type="button"
                            class="btn btn-primary"
                            style="float: right; margin: 3px"
                            data-mdb-toggle="modal"
                            data-mdb-target="#deleteConfirm"
                            :disabled="!containsExprMatched.edited"
                        >保存</button>
                    </div>
                </div>
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
        },
        osuApiToken: {
            type: String,
            required: false
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
            },

            containsExprMatched: {
                edited: false,
                comments: [],
                cachedBeatmapInfo: [],
            },

            editType: (path => {
                let sp = path.split("/");
                return sp[sp.length - 1];
            })(document.location.pathname)
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

            (await fetch("/ruleset/submit", {
                method: 'POST',
                body: JSON.stringify({
                    "token": token,
                    "type": 0,
                    "ruleset": {
                        "id": appRoot.editType === "new" ? 0 : Number(appRoot.editType),
                        "name": appRoot.ruleset.name,
                        "triggers": appRoot.ruleset.triggers.split(";"),
                        "expression": appRoot.ruleset.expression
                    }
                }),
            })).json().then(submitResult => {
                appRoot.processSubmitResult(submitResult, () => {
                    if(submitResult.newId !== 0 && appRoot.editType === "new") {
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

            if(appRoot.editType === "new") {
                appRoot.$emit("error-broadcast", "无法找到这个谱面类型规则，可能已经被删除。", 4000);
                return;
            }

            (await fetch("/ruleset/submit", {
                method: 'POST',
                body: JSON.stringify({
                    "token": token,
                    "type": 1,
                    "ruleset": { "id": Number(appRoot.editType) }
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
                    appRoot.$emit("error-broadcast", "提交修改时发生了内部错误：" + submitResult.errorMessage + "<br/>请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。", 10000);
                    break;
                }
            }
        },

        async checkAccess() {
            const appRoot = this;

            (await fetch("/ruleset/checkAccess", {
                method: 'POST',
                body: JSON.stringify({
                    "qq": appRoot.qq,
                    "editType": appRoot.editType === "new" ? 1 : 2,
                    "rulesetId": appRoot.editType === "new" ? 0 : Number(appRoot.editType)
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
                        appRoot.getBeatmapComment()
                        break;
                    }
                    case 1: {
                        appRoot.mainTitle = "谱面类型规则未找到";
                        appRoot.subTitle = "未找到 ID 为 " + appRoot.editType + " 的谱面类型规则，请确保 ID 正确。";
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

                        appRoot.$emit("error-broadcast", "检查权限时发生了内部错误：" + checkResponse.errorMessage + "<br/>请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。", 10000);
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
                        if(checkResponse.message.length) appRoot.getBeatmapComment();
                    } else {
                        appRoot.expressionSyntax.hasSyntaxError = true;
                        appRoot.expressionSyntax.message.push("ERROR: Internal error: " + checkResponse.errorMessage);
                        appRoot.$emit("error-broadcast", "语法检查发生了内部错误：" + checkResponse.errorMessage + "<br/>请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。", 10000);
                    }
                    appRoot.lastCheckedExpression = appRoot.ruleset.expression;
                });
            }
        },

        async getBeatmapComment() {
            if(this.editType === "new") return;

            const appRoot = this;

            let beatmap = (function () {
                let r = []

                function contains() { r = arguments; }
                let bid = new ColumnStub();
                let star = new ColumnStub();
                let bpm = new ColumnStub();
                let length = new ColumnStub();
                let ar = new ColumnStub();
                let od = new ColumnStub();
                let hp = new ColumnStub();
                let cs = new ColumnStub();
                let jump = new ColumnStub();
                let flow = new ColumnStub();
                let speed = new ColumnStub();
                let stamina = new ColumnStub();
                let precision = new ColumnStub();
                let accuracy = new ColumnStub();
                let recommendStar = Number();
                let matchIndex = Number();
                let matchGroup = Array();

                try { eval(appRoot.ruleset.expression); } catch (_) { }

                return r;
            }());

            (await fetch("/ruleset/getBeatmapComment", {
                method: 'POST',
                body: JSON.stringify({
                    "rulesetId": appRoot.editType === "new" ? 0 : Number(appRoot.editType),
                    "beatmap": beatmap["0"]
                }),
            })).json().then(response => {
                switch (Number(response.result)) {
                    case 0: {
                        appRoot.containsExprMatched.comments = response.comments;
                        appRoot.containsExprMatched.edited = false;
                        for(let c of response.comments) {
                            appRoot.cacheBeatmapInfo(c.bid)
                        }
                        break;
                    }
                    case -1: {
                        appRoot.$emit("error-broadcast", "获取谱面备注时发生了内部错误：" + response.errorMessage + "<br/>请前往 <a href='https://github.com/StageGuard/OsuMapSuggester' target='_blank'>GitHub<a/> 反馈这个问题。", 10000);
                        break;
                    }
                }
            });
        },

        async cacheBeatmapInfo(bid) {
            const appRoot = this;

            if(appRoot.containsExprMatched.cachedBeatmapInfo[String(bid)] == null) {
                (await fetch("https://osu.ppy.sh/api/v2/beatmaps/" + bid + "/", {
                    method: 'GET',
                    mode: 'no-cors',
                    headers: [["Authentication", 'Bearer ' + appRoot.osuApiToken]]
                })).json().then(response => {
                    if(response.version !== null && response.beatmapset !== null) {
                        appRoot.containsExprMatched.cachedBeatmapInfo[String(response.id)] = {
                            source: response.beatmapset.source,
                            title: response.beatmapset.title,
                            artist: response.beatmapset.artist,
                            difficulty: response.difficulty_rating,
                            version: response.version
                        }
                    }
                }).catch(ex => appRoot.$emit("warning-broadcast", "获取谱面 " + bid + " 信息时出错：" + ex));
            }
        },

        getBeatmapInfo(bid) {
            let info = appRoot.containsExprMatched.cachedBeatmapInfo[String(bid)];

            if (info == null) return ``;

            if (info.source) {
                return `<span class="text-info me-3"><b>` + info.source + `(` + info.artist + `) - ` + info.title + ` [` + info.version + `]</b></span>
                        <span class="me-1">` + info.version + `</span><i class="fa fa-star"></i>`;
            } else {
                return `<span class="text-info me-3"><b>` + info.artist + ` - ` + info.title + ` [` + info.version + `]</b></span>
                        <span class="me-1">` + info.version + `</span><i class="fa fa-star"></i>`;
            }
        }

    }
});

const ColumnStub = (function() {
    let clazz = function () {}
    clazz.prototype = {
        eq: (_) => new clazz(),
        notEq: (_) => new clazz(),
        and: (_) => new clazz(),
        or: (_) => new clazz(),
        xor: (_) => new clazz(),
        plus: (_) => new clazz(),
        minus: (_) => new clazz(),
        times: (_) => new clazz(),
        div: (_) => new clazz(),
        rem: (_) => new clazz(),
        less: (_) => new clazz(),
        greater: (_) => new clazz(),
        greaterEq: (_) => new clazz()
    }
    return clazz;
}());