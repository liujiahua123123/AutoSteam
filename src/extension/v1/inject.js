console.log("I injected!")
document.getElementById("i_agree_check").checked = true
setTimeout(function () {
    document.getElementById("account_form_box").getElementsByClassName("section_title")[0].innerText = "等待验证码"
},1550)
console.log("Agreement checked")

var intervalIndex = setInterval(function () {
    try {
        if (grecaptcha && grecaptcha.enterprise && grecaptcha.enterprise.getResponse().length > 0) {
            console.log("done!")
            clearInterval(intervalIndex)
            document.getElementById("createAccountButton").click()
        }
    }catch (e){

    }
},200)

setTimeout(function () {
    document.__HOOK__send = XMLHttpRequest.prototype.send
    Object.defineProperty(XMLHttpRequest.prototype, "send", {
        "value": function () {
            var data = arguments[0];
            if(data.indexOf("creationid") !== -1){
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
        }
    });

    console.log("Hooked XML prototype send")
},1);
console.log("Hooking XML")
