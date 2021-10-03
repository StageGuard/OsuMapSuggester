// noinspection JSUnusedGlobalSymbols

mainApp.component("ruleset-editor", {
    template: `
        <div class="row" v-show="show">
            <h2 class="mainTitle"><b>编辑谱面信息</b></h2>
            <h4 class="mainTitle"><b>{{ rulesetName }} by {{ rulesetCreator }}</b></h4>
            <!-- form area -->
            <div class="col-sm-5 mainColumn">
                <form class="card bg-light" @submit.prevent="submitRuleset">
                    <div class="card-header"><b>谱面规则信息</b></div>
                    <div class="card-body">
                    
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则名称</b><br><small>为你的规则设置名称，用于显示在推图结果中。</small>
                            </h6>
                            <input type="text" v-model="rulesetName" class="form-control form-control-lg" required/>
                        </div>
                        
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则触发词</b><br><small>为你的规则设置触发词，用于触发你的规则<br/>允许正则表达式，不允许空格。</small>
                            </h6>
                            <input type="text" v-model="rulesetTriggers" class="form-control form-control-lg" required/>
                        </div>
                        
                        <div class="form formItem">
                            <h6 class="form-label" style="line-height: 150%">
                                <b>规则表达式</b><br><small>为你的规则设置 JavaScript 匹配表达式。<br/>访问 <a href="https://github.com/StageGuard/OsuMapSuggester/wiki/Beatmap-Ruleset-Expression" target="_blank">Beatmap Ruleset Expression</a> 获取更多信息。</small>
                            </h6>
                            <textarea type="text" v-model="rulesetExpression" class="form-control form-control-lg" required/>
                        </div>
                        
                        <button type="submit" class="btn btn-primary" style="float: right">保存</button>
                    </div>
                </form>
            </div>
            <div class="col-sm-7 mainColumn">
                .col-sm-7
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

    data() {
        return {
            rulesetName: "abc",
            rulesetTriggers: "triggers",
            rulesetExpression: "expression",
            rulesetCreator: "creator"
        }
    },

    async created() {
        console.log("editor created")

    },

    methods: {
        async submitRuleset() {
            alert("Ruleset Name: " + this.rulesetName + "\nRuleset Triggers: " + this.rulesetTriggers + "\nRuleset Expression: " + this.rulesetExpression)
        },

        async checkAccess() {
            let editType = (path => {
                let sp = path.split("/")
                return sp[sp.length - 1]
            })(document.location.pathname);

            (await fetch("/ruleset/checkAccess", {
                method: 'POST',
                body: JSON.stringify({
                    "qq": verifyResponse.qq,
                    "editType": editType === "new" ? 1 : 2,
                    "rulesetId": editType === "new" ? 0 : Number(editType)
                }),
            })).json().then(checkResponse => {
                console.log(checkResponse)
                switch (Number(checkResponse.result)) {
                    case 0: {
                        appRoot.verifyResult = "\n Have permission to ";
                        if (checkResponse.ruleset == null) {
                            appRoot.verifyResult += "create a new ruleset.";
                        } else {
                            appRoot.verifyResult += "edit ruleset " + checkResponse.ruleset.name;
                        }
                        break;
                    }
                    case 1: {
                        appRoot.verifyResult = "Ruleset " + Number(editType) + " not found"
                        break;
                    }
                    case 2: {
                        appRoot.verifyResult = "\n Not the creator of ruleset " + checkResponse.ruleset.name;
                        break;
                    }
                    case -1: {
                        appRoot.verifyResult = "Internal error: " + checkResponse.errorMessage;
                        break;
                    }
                }
            })
        }
    }
})