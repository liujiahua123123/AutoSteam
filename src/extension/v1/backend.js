const host = "http://127.0.0.1:9989"
var tabId = -1;

chrome.runtime.onMessage.addListener(
    function (request, sender, sendResponse) {
        if (request.init) {
            tabId = sender.tab.id;

            let xhr = new XMLHttpRequest();
            xhr.open("GET", host + "/email", true);
            xhr.send("")
            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    let nextMail = xhr.responseText
                    console.log("next mail is " + nextMail)
                    chrome.tabs.sendMessage(tabId, {email: nextMail})

                    console.log("injecting... ")
                    loadFile("inject.js", content => {
                        chrome.tabs.sendMessage(tabId, {content: content})
                    })
                }
            }
        } else if (request.address) {
            console.log("submit received: " + request.session)
            let submit = new XMLHttpRequest();
            submit.open("GET", host + "/newJoinSession/v1?address=" + request.address + "&session=" + request.session, true);
            submit.send("")
            submit.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    console.log("Sent to backend complete!")
                }
            }


            let xhr = new XMLHttpRequest();
            xhr.open("GET", host + "/email", true);
            xhr.send("")
            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    let nextMail = xhr.responseText
                    console.log("next mail is " + nextMail)
                    chrome.tabs.sendMessage(tabId, {email: nextMail})
                    chrome.tabs.sendMessage(tabId, {refresh: "OK"})
                }
            }
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
