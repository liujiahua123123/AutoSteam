var tabId = -1;
const host = "http://127.0.0.1:9989/cn"

chrome.webRequest.onBeforeRequest.addListener(
    function(details) {
        if (details.url.indexOf(host) !== -1 || details.url.indexOf("secCode") === -1) {
            return {}
        }

        console.log("Detected A SMS Request, canceling it")
        // 直接生成新页面，进行重定向
        console.log(details.url)

        let xhr = new XMLHttpRequest();
        xhr.open("GET", details.url.replace("https://rnr.steamchina.com/securityCode",host), true);
        xhr.send("")
        xhr.onreadystatechange = function () {
            if (xhr.readyState === 4) {
                console.log("Sent to backend")
                chrome.tabs.sendMessage(tabId, {
                    refresh: true
                }, function(response) {});
            }
        }

        return {cancel: true};


       // return {redirectUrl: "data:application/json;charset=UTF-8;base64," +  "eyJjb2RlIjowLCJkZXNjcmlwdGlvbiI6IlN1Y2Nlc3MiLCJyZXN1bHQiOnsiY2FwVGlja2V0IjoiRkFLRU1FIn19"};
    },
    {urls:  ["<all_urls>"]},
    ["blocking", "requestBody"]
);

chrome.runtime.onMessage.addListener(
    function (request, sender, sendResponse) {
        if (request.init) {
            tabId = sender.tab.id;
            console.log("injecting... ")
            loadFile("inject.js", content => {
                chrome.tabs.sendMessage(tabId, {content: content})
            })

        } else if (request.address) {

        } else {
            console.log(request);
        }
        sendResponse(null);
    }
);


chrome.webRequest.onErrorOccurred.addListener(function (details) {
    if (details.type === "main_frame") {
        console.log("加载失败")
        setTimeout(function () {
            refreshChromeInstance(function () {
                withNextProxy(function (proxy) {
                    setupProxy(proxy)
                })
            })
        }, 2000);
    }
}, {
    urls: ['<all_urls>']
})

function loadFile(path, fun) {
    chrome.runtime.getPackageDirectoryEntry(function (dirEntry) {
        dirEntry.getFile(path, undefined, function (fileEntry) {
            fileEntry.file(function (file) {
                var reader = new FileReader()
                reader.addEventListener("load", function (event) {
                    // data now in reader.result
                    fun(reader.result)
                });
                reader.readAsText(file);
            });
        }, function (e) {
            console.log(e);
        });
    });
}
