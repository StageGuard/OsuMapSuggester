// JSUnusedGlobalSymbols
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
        let token = getCookie("token")
        if(token) {
            (await fetch("/ruleset/verify", {
                method: 'POST',
                body: JSON.stringify({
                    "token": token
                }),
            })).json().then(data => {
                switch (Number(data.result)) {
                    case 0: {
                        appRoot.verifyResult = "Verify successful.";
                        appRoot.verifyResult += "\nBind QQ: " + data.qq;
                        appRoot.verifyResult += "\nOsu info: " + data.osuName + "(" + data.osuId + ")";
                        break;
                    }
                    case 1: {
                        appRoot.verifyResult = "Cookie not found.";
                        break;
                    }
                    case 2: {
                        appRoot.verifyResult = "Not bind.";
                        appRoot.verifyResult += "\nOsuId: " + data.osuId;
                        break;
                    }
                    case 3: {
                        appRoot.verifyResult = "Already unbound.";
                        appRoot.verifyResult += "\nBind QQ: " + data.qq;
                        break;
                    }
                    case -1: {
                        appRoot.verifyResult = "Internal error: " + data.errorMessage;
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
                body: JSON.stringify({
                    "callback": document.location.pathname
                }),
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