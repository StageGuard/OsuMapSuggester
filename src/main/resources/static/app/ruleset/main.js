// noinspection JSUnusedGlobalSymbols

const mainApp = Vue.createApp({

    template: `
        <!-- Top-right toast -->
        <!--
             data-mdb-width="535px" 
            style="width: 535px; top: 10px; right: 10px; bottom: unset; left: unset; transform: unset;"
            :style="{ display: toast.displayShowing ? 'block' : 'none' }"
        -->
        <div 
            class="alert fade alert-fixed shadow-3" :class="{ 
                'show': toast.classShowing,
                'alert-success': toast.type === 0,
                'alert-warning': toast.type === 1,
                'alert-danger': toast.type === 2,
                'alert-info': toast.type !== 0 && toast.type !== 1 && toast.type !== 2,
            }"
            id="global-alert" role="alert"
            aria-live="assertive" aria-atomic="true" :data-mdb-width="alertWidth" 
            data-mdb-color="primary"
            data-mdb-stacking="true" data-mdb-append-to-body="true"
            data-mdb-hidden="true" data-mdb-autohide="true"
            :data-mdb-delay="toast.duration"
            style="top: 10px; right: 10px;"
            :style = "{ width: alertWidth }"
            v-html="toast.content"
        ></div>
        
        <authorize 
            :show="showAuthorizationDialog" 
            @verified-broadcast="initRulesetEditor"
            @set-osu-api-token="setOsuApiToken"
            @error-broadcast="toastError"
        ></authorize>
        <ruleset-editor
            :qq="qq"
            :show="showRulesetEditor"
            :osuApiToken="osuApiToken"
            @error-broadcast="toastError"
            @warning-broadcast="toastWarning"
            @success-broadcast="toastSuccess"
        ></ruleset-editor>
    `,

    data() {
        return {
            toast: {
                type: 0,
                content: "",
                duration: 3500
            },
            showAuthorizationDialog: true,
            showRulesetEditor: false,
            alertWidth: "582px",
            qq: Number(),
            osuId: Number(),
            osuName: String(),
            osuApiToken: String()
        }
    },

    created() {
        window.onresize = this._onWindowResize
    },

    methods: {
        initRulesetEditor(qq, osuId, osuName) {
            this.qq = qq;
            this.osuId = osuId;
            this.osuName = osuName;

            this.showAuthorizationDialog = false;
            this.showRulesetEditor = true;
        },

        setOsuApiToken(token) {
            this.osuApiToken = token;
        },

        _onWindowResize() {
            let width = window.screen.width;

            if(width >= 600) {
                this.alertWidth = "582px";
            } else {
                this.alertWidth = (width - 20) + "px";
            }
        },

        _toast(contentHTML, duration, type) {
            this.toast.type = type || 0;
            this.toast.content = contentHTML || "No content.";
            this.toast.duration = duration || 3500;

            const globalToast = document.getElementById("global-alert")
            const toast = new mdb.Toast(globalToast);
            toast.show();
        },

        toastError(message, duration) {
            this._toast(
                '<i class="fa fa-times-circle me-3"></i> ' + message,
                duration || 8500, 2
            );
        },

        toastWarning(message, duration) {
            this._toast(
                '<i class="fa fa-exclamation-triangle me-3"></i> ' + message,
                duration || 4500, 1
            );
        },

        toastSuccess(message, duration) {
            this._toast(
                '<i class="fa fa-check-circle me-3"></i> ' + message,
                duration || 3000, 0
            );
        },

        toastInfo(message, duration) {
            this._toast(
                '<i class="fa fa-info-circle me-3"></i> ' + message,
                duration || 3000, 3
            );
        },
    }
})