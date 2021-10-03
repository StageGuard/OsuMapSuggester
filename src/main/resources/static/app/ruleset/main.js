// noinspection JSUnusedGlobalSymbols

const mainApp = Vue.createApp({

    template: `
        <authorize 
            :show="showAuthorizationDialog" 
            @verified-broadcast="initRulesetEditor"
        ></authorize>
        <ruleset-editor
            :qq="qq"
            :show="showRulesetEditor"
        ></ruleset-editor>
    `,

    data() {
        return {
            showAuthorizationDialog: true,
            showRulesetEditor: false,
            qq: Number(),
            osuId: Number(),
            osuName: String(),
        }
    },

    methods: {
        initRulesetEditor(qq, osuId, osuName) {
            this.qq = qq;
            this.osuId = osuId;
            this.osuName = osuName;

            this.showAuthorizationDialog = false
            this.showRulesetEditor = true
        }
    }
})