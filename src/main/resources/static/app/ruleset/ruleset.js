// noinspection JSUnusedGlobalSymbols

const getCookie = function (key) {
    let cookieString = ""
    document.cookie.split(';').forEach(e => {
        let value = e.trim();
        if(value.startsWith(key + "="))
            cookieString = value.replace(key + "=", "");
    })
    return cookieString;
}
const rulesetApp = Vue.createApp({
    template: `
    <div class="card" style="width:350px">
        <div class="card-body">
            <h5 class="card-title">Cookie</h5>
            <p class="card-text">{{ cookie }}</p>
            <p class="card-text">{{ verifyResult }}</p>
            <p class="card-text">{{ verifyLink }}</p>
            <button class="btn btn-primary" @click="getVerifyLink()">Create OAuth Link</button>
        </div>
    </div>`,

    data() {
        return {
            cookie: document.cookie,
            verifyResult: "verifyResult",
            verifyLink: "oAuth link",
        }
    },

    async created() {
        const appRoot = this;
        let token = getCookie("token");

        if(token) {
            (await fetch("/ruleset/verify", {
                method: 'POST',
                body: JSON.stringify({ "token": token }),
            })).json().then(async verifyResponse => {
                console.log(verifyResponse)
                switch (Number(verifyResponse.result)) {
                    case 0: {
                        appRoot.verifyResult = "Verify successful.";

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
                                    if(checkResponse.ruleset == null) {
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
                        break;
                    }
                    case 1: {
                        appRoot.verifyResult = "Cookie not found.";
                        break;
                    }
                    case 2: {
                        appRoot.verifyResult = "Not bind.";
                        appRoot.verifyResult += "\nOsuId: " + verifyResponse.osuId;
                        break;
                    }
                    case 3: {
                        appRoot.verifyResult = "Already unbound.";
                        appRoot.verifyResult += "\nBind QQ: " + verifyResponse.qq;
                        break;
                    }
                    case -1: {
                        appRoot.verifyResult = "Internal error: " + verifyResponse.errorMessage;
                        break;
                    }
                }
            });
        } else {
            appRoot.verifyResult = "Not authorized.";
        }


    },

    methods: {
        async getVerifyLink() {
            const appRoot = this;
            (await fetch("/ruleset/getVerifyLink", {
                method: 'POST',
                body: JSON.stringify({ "callback": document.location.pathname }),
            })).json().then(data => {
                if(data.result === 0) {
                    appRoot.verifyLink = "Succeed: " + data.link
                } else {
                    appRoot.verifyLink = "Failed: " + data.errorMessage
                }
            });
        }
    }
})