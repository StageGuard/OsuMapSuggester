const getCookie = function (key) {
    let cookieString = ""
    document.cookie.split(';').forEach(e => {
        let value = e.trim();
        if (value.startsWith(key + "="))
            cookieString = value.replace(key + "=", "");
    })
    return cookieString;
}

mainApp.component("authorize", {
    template: `
        <div v-show="show">
            <div class="card text-center border border-primary shadow-0 mb-3">
                <div class="card-header h5"><b>验证 osu! 账号</b></div>
                <div class="card-body text-primary">
                    <h5 class="card-title" style="margin-bottom: 10px"> {{ verifySummary }}</h5>
                    <p class="card-text" v-html="verifyMessage"></p>
                    <a :href="verifyLink" class="btn btn-primary" v-show="showVerifyButton">前往 oAuth 授权</a>
                </div>
            </div>
        </div>
    `,

    props: {
        show: {
            type: Boolean,
            required: true
        }
    },

    data() {
        return {
            showVerifyButton: false,
            verifySummary: "验证请求中",
            verifyMessage: "很快就好...",
            verifyLink: "#",
        }
    },

    async created() {
        const appRoot = this;
        let token = getCookie("token");

        if (token) {
            (await fetch("/ruleset/verify", {
                method: 'POST',
                body: JSON.stringify({"token": token}),
            })).json().then(async verifyResponse => {
                let showButton = false
                switch (Number(verifyResponse.result)) {
                    case -1: {
                        appRoot.verifySummary = "内部错误";
                        appRoot.verifyMessage = verifyResponse.errorMessage + "<br/>";
                        appRoot.verifyMessage += "请前往 <a href='https://github.com/StageGuard/OsuMapSuggester'>GitHub<a/> 反馈这个问题。"
                        break;
                    }
                    case 0: {
                        appRoot.verifySummary = "验证成功";
                        appRoot.verifyMessage = "绑定信息 : QQ(<b>" + verifyResponse.qq + "</b>), osu!Id(<b>" + verifyResponse.osuId + "</b>)"

                        appRoot.$emit("verified-broadcast", verifyResponse.qq, verifyResponse.osuId, verifyResponse.osuName)
                        break;
                    }
                    case 1: {
                        appRoot.verifySummary = "验证失败：会话失效";
                        appRoot.verifyMessage = "当前会话已失效，请重新验证。<br/>"
                        appRoot.verifyMessage += "发生这种情况一般是因为你在另一个浏览器绑定了你的账号。<br/>"
                        appRoot.verifyMessage += "在另一个浏览器绑定成功后这个浏览器的会话就会失效。<br/>"
                        appRoot.verifyMessage += "请点击下方按钮重新认证。<br/>"
                        showButton = true
                        break;
                    }
                    case 2: {
                        appRoot.verifySummary = "验证失败：未绑定";
                        appRoot.verifyMessage = "此 osu! 账号(Id: <b>" + verifyResponse.osuId  + "<b/>)并未绑定 QQ 账号。<br/>"
                        appRoot.verifyMessage += "请加入一个包含此 BOT 的群绑定你的 QQ。<br/>"
                        appRoot.verifyMessage += "绑定成功后点击下方按钮重新认证。<br/>"
                        showButton = true
                        break;
                    }
                    case 3: {
                        appRoot.verifySummary = "验证失败：已解绑";
                        appRoot.verifyMessage = "此 osu! 账号(Id: <b>" + verifyResponse.osuId  + "<b/>)已与 QQ 号(<b>" + verifyResponse.qq  + "</>)解除绑定。<br/>"
                        appRoot.verifyMessage += "若想继续编辑，请重新绑定你的 QQ。<br/>"
                        appRoot.verifyMessage += "绑定成功后点击下方按钮重新认证。<br/>"
                        showButton = true
                        break;
                    }
                }
                if(showButton) await appRoot.getVerifyLink(data => {
                    if(Number(data.result) === 0) {
                        appRoot.verifyLink = data.link
                        appRoot.showVerifyButton = true
                    } else {
                        appRoot.verifyMessage += "<span style='color: #dc3545'>无法获取验证链接：<br/>"
                        appRoot.verifyMessage += data.errorMessage +"<br/>"
                        appRoot.verifyMessage += "请前往 <a href='https://github.com/StageGuard/OsuMapSuggester'>GitHub<a/> 反馈这个问题。</span>"
                    }
                })
            });
        } else {
            appRoot.verifySummary = "初次编辑，请验证你的 osu! 账号";
            appRoot.verifyMessage = "点击下方按钮进行验证。"
        }


    },

    methods: {
        async getVerifyLink(cbk) {
            const appRoot = this;
            (await fetch("/ruleset/getVerifyLink", {
                method: 'POST',
                body: JSON.stringify({"callback": document.location.pathname}),
            })).json().then(cbk);
        }
    }
})