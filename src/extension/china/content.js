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

        }

        if (request.refresh) {

        }

    })

    document.addEventListener('SessionSubmitEvent', function (ev) {
        chrome.runtime.sendMessage(ev.detail.data)
    })

    document.title = "正在注入"
    chrome.runtime.sendMessage({
        init: window.location.href
    })

})();

