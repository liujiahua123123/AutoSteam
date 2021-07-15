setTimeout(function () {
    document.__HOOK__send = XMLHttpRequest.prototype.send
    Object.defineProperty(XMLHttpRequest.prototype, "send", {
        "value": function () {
            var data = arguments[0];
            if(data.indexOf("email") !== -1){
                console.log(decodeURI(data))
                var dms = decodeURI(data).split("&")
                for (var i=0;i<dms.length;++i){
                    var datum = dms[i].split("=");
                    if(datum[0] === "captchagid" || datum[0] === "captcha_text"){
                        console.log(datum[1])
                    }
                }
            }else{
                document.__HOOK__send.apply(this, arguments)
            }
        }
    });

    console.log("Hooked XML prototype send")
},10);

var x = new XMLHttpRequest()
x.open("POST","")

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


console.log("document.getElementById(\"captchagid\").value = " + document.getElementById("captchagid").value)

console.log("document.getElementById(\"recaptcha-token\").value =" + document.getElementById("recaptcha-token").value)


var formData = new FormData();
formData.append("email","testemail@myemail.cc")
formData.append("captchagid","3994113960091208303")
formData.append("captcha_text","03AGdBq25m3Zpo1LZEkH2wfT9KjNnHgXyfMJCzZRi0MffWDwmB4crkdWYNuKAxoIoe0npXfo6-5GjnxJUUIlKfV64G9vsmORhZ3r4MKbQDyBjiNcGzt96cw-nyZvkl33UsRCMTWsCwtD4sLAiID9Sxe5sa_7R5Pat0pdM1F4eSYltom75zPi6xLY4IQ9O0NPNGv5VsdndCUDfI2o4fI8Z2eV66uve338kWGfYAVoGKIVYWGoWJVyawYwV-EBujgrd_1fXe8o1k8nM5ck56Z-r2a53qySyFv7Buz2XBc8HmxX0kNjJmE7dC56MWHAlb4C_UyExcKArvX9qtxcvOlLfvDHsnyUCh8z9k8zKEfN6h1rLx-vPhm9siC8bxWlZc87qZagN8f4vGOM7KT5L9hbRKsEIeQjT5uVtqhb67w_RTpvAPdVuazScXJXyUkNjLpXEK1YDMvW6BmHlRxdfjl0XoEi0PeGHJMGSWlVnZijTuF7oBgv6D_H4UskzXjgUlovqYH2iOhvOG3u4css0twszMOX7svXy4aKB0GR4XgBmM1QQLPKOLY2SF0DPNzyarFfpbFynlDg4CdbhODnmGG8K3cUEaHEYrtyeiTIsPR4Ds8q0DIhdatILfjgsE21ZnB3moJhfpcG9WybpePzzN6N_T4NY3jpKJ7hm8JaHIopspH22_LaYKhyqulYnkez_XEfMc95s51NCQbTZ_CMBT4DwasIlamb2VCYKHD5ELQQkKHZImEYMkhsNjZcubY2KAALAGc95_nOjht1vxS-EynoSsS2GJ8u_N0AISjVtCqcNS2IngKcGIG4_DNaunO2Yhu-gFN5-0bUlH314TQHof8AHo3v2htuIy1LOlS4p89v7Oqg5VXE-UCByYfUgaC1EBuNRHhvLvQWV5pENezzVLyW0vUER7-DIwif4msWt_1SeL97S56tA6pl9mzmZDojPplxkfPL4h2Cge8EWLsFeNhebQm2VVzsyIHZx-sCeNo43cDttZbQtDo3vwnsAAaKw4xRvxD8W-cggDtLQXzph8POOXzj8ftbgaL9i-9hg_bEU36Y47zFvYGGqtt3Eqqa4MHbH0HrmqEW-QqOHAAf_fbWwhXkNLODtr0AxW1IyyPZZ85XvYRNgIwuUBfFDb1-ZAyWVfw7dTd0a9wRinafQRqwrs6GbZFKnx71dG-UUOQYitWiCrIbFcJu_-qbRgPQIPS6a4WxrL44nqkBZVb6d8Ua-Bq_IgGqS3EozLk_v9pKnPoyqp3WU3fl4GxYzgZg9iCfhlunwUIysDDT5FUshOCdarl_33fKsQgGFVZjaQKzhLGkuRPJ77QZEqCZVEStVyBrkgg-3PANk0xB1vq_WKWEwY0j--jHlDIze9BkTbxg5p5EjcAX2x6WIXE1-zFwkeJaEpuCd7kDMPtDWhPxENSMSBAGF1_-rrPTxnXaJ33GB2gr8lAT3C4DGJT6nhXd9ABnTX6s7vdxohhuPYJe4vZrvFxmmaqiygZIkHmdLgbk_Df_MlCkxMW7ozUG1KMh09Utzo_KvpMxo_5gY_E6pNr1fPngzpTh9L9J_jzLEov0bbmXfdawSSnR4adetJsHCAZYnUh1qdgFE8joDrVgEpnpSvkTlRnlnYajvCxQ")

var httpRequest = new XMLHttpRequest();
httpRequest.open("POST", "https://store.steampowered.com/join/ajaxverifyemail", true);
httpRequest.onload = function(e){
    console.log(e.target.responseText);
}
httpRequest.send(formData);