console.log("I injected!")

XMLHttpRequest.prototype.send = sendBypass(XMLHttpRequest.prototype.send);
