// noinspection JSUnusedGlobalSymbols

const mainApp = Vue.createApp({

    template: `
        <!-- Top-right toast -->
        <div 
            class="alert fade alert-fixed shadow-3" :class="{ 
                'show': toast.classShowing,
                'alert-success': toast.type === 0,
                'alert-warning': toast.type === 1,
                'alert-danger': toast.type === 2,
                'alert-info': toast.type !== 0 && toast.type !== 1 && toast.type !== 2,
            }"
            id="alert-primary" role="alert" 
            data-mdb-color="primary" data-mdb-position="top-right" 
            data-mdb-stacking="true" data-mdb-width="535px" 
            data-mdb-append-to-body="true" data-mdb-hidden="true" 
            data-mdb-autohide="true" data-mdb-delay="2000" 
            style="width: 535px; top: 10px; right: 10px; bottom: unset; left: unset; transform: unset;"
            :style="{ display: toast.displayShowing ? 'block' : 'none' }"
            v-html="toast.content"
        ></div>
        
        <authorize 
            :show="showAuthorizationDialog" 
            @verified-broadcast="initRulesetEditor"
            @error-broadcast="toastError"
        ></authorize>
        <ruleset-editor
            :qq="qq"
            :show="showRulesetEditor"
            @error-broadcast="toastError"
            @warning-broadcast="toastWarning"
            @success-broadcast="toastSuccess"
        ></ruleset-editor>
    `,

    data() {
        return {
            toast: {
                classShowing: false,
                displayShowing: false,
                type: 0,
                content: ""
            },
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
        },

        _toast(contentHTML, duration, type) {
            this.toast.type = type || 0;
            this.toast.content = contentHTML || "No content.";
            this.toast.displayShowing = true;
            // delay for animation
            setTimeout(() => { this.toast.classShowing = true; }, 50);

            new Promise(resolve => {
                 setTimeout(() => {
                     this.toast.classShowing = false;
                     resolve();
                 }, duration || 3500);
            }).then(_ => {
                setTimeout(() => {
                    this.toast.displayShowing = false;
                }, 250);
            });
        },

        toastError(message, duration) {
            this._toast(
                '<i class="fa fa-times-circle"></i> ' + message,
                duration || 8500, 2
            );
        },

        toastWarning(message, duration) {
            this._toast(
                '<i class="fa fa-exclamation-triangle"></i> ' + message,
                duration || 4500, 1
            );
        },

        toastSuccess(message, duration) {
            this._toast(
                '<i class="fa fa-check-circle"></i> ' + message,
                duration || 3000, 0
            );
        },

        toastInfo(message, duration) {
            this._toast(
                '<i class="fa fa-info-circle"></i> ' + message,
                duration || 3000, 3
            );
        },
    }
})