console.log("I injected!")
document.getElementById("i_agree_check").checked = true
setTimeout(function () {
    document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "等待验证码"
},1550)
console.log("Agreement checked")

var intervalIndex = startChecker()

function startChecker(){
    return setInterval(function () {
        try {
            if (grecaptcha && grecaptcha.enterprise && grecaptcha.enterprise.getResponse().length > 0) {
                console.log("done, submitting in background")
                clearInterval(intervalIndex)
                document.getElementById("createAccountButton").disabled = false
                document.getElementById("createAccountButton").click()
            }
        }catch (e){

        }
    },200)
}



document.addEventListener('RefreshCaptchaEvent', function (ev) {
    RefreshCaptcha()
    document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "刷新验证码"
    var waitForRefresh = setInterval(function () {
        if (grecaptcha && grecaptcha.enterprise && grecaptcha.enterprise.getResponse() === "") {
            document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "等待验证码"
            clearInterval(waitForRefresh)
            intervalIndex = startChecker()
        }
    })
})

setTimeout(function () {
    document.__HOOK__send = XMLHttpRequest.prototype.send
    Object.defineProperty(XMLHttpRequest.prototype, "send", {

        "value": function () {
            try {
                var data = arguments[0];
                if (data.indexOf("email") !== -1 && data.indexOf("yellow") === -1) {
                    //console.log(decodeURI(data))
                    var dms = decodeURI(data).split("&")

                    var email = document.getElementById("email").value
                    var formData = new FormData();

                    for (var i = 0; i < dms.length; ++i) {
                        var datum = dms[i].split("=");
                        if(datum[0] === "captchagid"){
                            formData.append("captchagid",datum[1])
                        }
                    }

                    formData.append("email",email)
                    formData.append("captcha_text",grecaptcha.enterprise.getResponse())
                    formData.append("elang","6")
                    formData.append("yellow","stopMe")

                    console.log("Email=" + email)

                    var httpRequest = new XMLHttpRequest();
                    httpRequest.open("POST", "https://store.steampowered.com/join/ajaxverifyemail", true);
                    httpRequest.onload = function (e) {
                        var response = JSON.parse(e.target.responseText);
                        console.log(response)
                        if (response.success !== 1) {
                            alert("Error")
                            return
                        }
                        console.log("sessionId " + response.sessionid)

                        let ev = new CustomEvent('SessionSubmitEvent', {
                            detail: {
                                data: {
                                    address: email,
                                    session: response.sessionid,
                                }
                            }
                        })
                        document.dispatchEvent(ev)
                    }

                    httpRequest.setRequestHeader("X-Requested-With","XMLHttpRequest")

                    console.log("Sending req.")
                    httpRequest.send(formData);
                    console.log("Req send")
                } else {
                    document.__HOOK__send.apply(this, arguments)
                }
            } catch (e) {
                console.log("UN")
                console.log(e)
                document.__HOOK__send.apply(this, arguments)
            }
        }
    });

    console.log("Hooked XML prototype send")
},1);
console.log("Hooking XML")

/**
 * if(data.indexOf("creationid") !== -1){
                console.log("Caught creation sesssionID (creationID)")

                console.log(data)
                var sessionId = data.replace("creationid=","")
                var email = document.getElementById("email").value

                document
                    .getElementsByClassName("newmodal")[0]
                    .getElementsByClassName("title_text")[0].innerText = "正在发送至后端"

                document
                    .getElementsByClassName("newmodal")[0]
                    .getElementsByClassName("LoadingText")[0].innerText = "正在处理"


                let ev = new CustomEvent('SessionSubmitEvent', {
                    detail: {
                        data: {
                            address: email,
                            session: sessionId,
                        }
                    }
                })
                document.dispatchEvent(ev)

            }else{
                document.__HOOK__send.apply(this, arguments)
            }
 */
