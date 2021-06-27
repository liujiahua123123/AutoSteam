(function () {

    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (request.content) {
            var script = document.createElement("script");
            script.textContent = request.content;
            if (document.head) {
                document.head.appendChild(script);
            } else if (document.documentElement) {
                document.documentElement.appendChild(script);
            }
            document.title = "注入完成"
        }

        if(request.email){
            var email = request.email;
            document.getElementById("email").value = email
            document.getElementById("reenter_email").value = email
            document.getElementById("i_agree_check").checked = true
            document.getElementById("createAccountButton").disabled = false
            document.getElementById("createAccountButton").innerText = "已完成验证码但未自动刷新"
            document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "Waiting For Game"
        }

        if (request.refresh) {
            let url = request.refresh;
            if (window.location.href !== url) {
                setTimeout(function () {
                    window.location.href = url
                }, 2000)////ensure all request are complete
            } else {
                try{
                    document
                        .getElementsByClassName("newmodal")[0]
                        .getElementsByClassName("title_text")[0].innerText = "发送完成, 正在刷新"


                }catch (e){

                }
                window.location.reload()
            }
        }

    })

    document.addEventListener('SessionSubmitEvent', function (ev) {
        chrome.runtime.sendMessage(ev.detail.data)
    })

    if(window.location.href.indexOf("join")!==-1) {
        document.getElementById("createAccountButton").disabled = true
        document.getElementById("email").disabled = true
        document.getElementById("reenter_email").disabled = true
        document.getElementById("email").value = "正在获取..."
        document.getElementById("reenter_email").value = "正在获取..."
        document.getElementById("createAccountButton").innerText = "正在注入"
        document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "Preparing..."

        document.title = "正在注入"
        chrome.runtime.sendMessage({
            init: window.location.href
        })
    }


})();

