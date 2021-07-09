setTimeout(function () {
    document.__HOOK__send = XMLHttpRequest.prototype.send
    Object.defineProperty(XMLHttpRequest.prototype, "send", {
        "value": function () {
            var data = arguments[0];
            if(data.indexOf("creationid") !== -1){
                console.log("Catched creation sesssionID (creationID)")
                console.log(data)
            }else{
                document.__HOOK__send.apply(this, arguments)
            }
        }
    });

    console.log("Hooked XML prototype send")
},10);


setTimeout(function () {
    document.__HOOK__send = XMLHttpRequest.prototype.send
    Object.defineProperty(XMLHttpRequest.prototype, "send", {
        "value": function () {
            var data = arguments[0];
            console.log("Send: " + data)
            if(data.indexOf("secCode") !== -1){
                console.log("Catched creation secCode (SC Captcha secCode)")
                console.log(data)
            }else{
                document.__HOOK__send.apply(this, arguments)
            }
        }
    });

    Object.defineProperty(XMLHttpRequest.prototype, "open", {
        "value": function () {
            var data = arguments[0];
            console.log("Open: " + data)
        }
    });

    console.log("Hooked XML prototype send")
},10);