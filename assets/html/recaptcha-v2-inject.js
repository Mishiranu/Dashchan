function recaptchaCommonEvent(i, n) {
	// click, keyup, ...
	var d = i.ownerDocument
	var e = d.createEvent('HTMLEvents')
	e.initEvent(n, true, true)
	i.dispatchEvent(e)
}

function recaptchaMouseEvent(i, n) {
	// mousedown, mouseup, ...
	var d = i.ownerDocument
	var e = d.createEvent('MouseEvents')
	e.initMouseEvent(n, true, true, d.defaultView, 1, 0, 0, 0, 0, false, false, false, false, 0, null)
	i.dispatchEvent(e)
}

var frameFunctions = []

function declareFrameFunction(index, name, call) {
    frameFunctions.push({index: index, name: name, call: call})
}

function getFrameFunction(index, name) {
    for (var ffi = 0; ffi < frameFunctions.length; ffi++) {
        var frameFunction = frameFunctions[ffi]
        if (frameFunction.index == index && frameFunction.name == name) {
            return frameFunction.call
        }
    }
}

function recaptchaResetStatus() {
    var e = document.getElementById('recaptcha-accessible-status')
    e.innerHTML = 'Empty status'
}

declareFrameFunction(0, 'recaptchaStartCheck', function() {
	recaptchaCommonEvent(document.getElementById('recaptcha-anchor'), 'click')
	recaptchaResetStatus()
})

declareFrameFunction(1, 'recaptchaStartVerify', function() {
	var verifyButton = document.getElementById('recaptcha-verify-button')
	recaptchaMouseEvent(verifyButton, 'mousedown')
	recaptchaMouseEvent(verifyButton, 'mouseup')
	recaptchaMouseEvent(verifyButton, 'click')
})

declareFrameFunction(0, 'recaptchaCheckCaptchaExpired', function() {
	var expired = 'true'
	try {
		var e = document.getElementById('recaptcha-accessible-status')
		var text = e.innerHTML
		if (text.indexOf('expired') == -1) expired = 'false'
		recaptchaResetStatus()
	} finally {
		jsi.onCheckCaptchaExpired(workerUuid, expired)
	}
})

declareFrameFunction(1, 'recaptchaCheckImageSelect', function() {
	var imageSelector = ''
	var description = ''
	var sizeX = 3
	var sizeY = 3
	try {
		if (document.getElementById('rc-imageselect') != null) {
			imageSelector = 'true'
			var descriptionObject = (document.getElementsByClassName('rc-imageselect-desc-no-canonical')[0] ||
					document.getElementsByClassName('rc-imageselect-desc')[0])
			if (descriptionObject) {
				description = descriptionObject.innerHTML
			}
			var e = document.getElementById('rc-imageselect-target')
			if (e) {
				e = e.children[0]
				var trs = e.getElementsByTagName('tr')
				var tds = e.getElementsByTagName('td')
				trs = trs && trs.length || sizeY
				tds = tds && tds.length || sizeX * sizeY
				sizeY = trs
				sizeX = tds / trs | 0
			}
		}
	} finally {
		jsi.onCheckImageSelect(workerUuid, imageSelector, description, sizeX, sizeY)
	}
})

declareFrameFunction(1, 'recaptchaCheckImageSelectedTooFew', function() {
	var few = ''
	var checked = ''
	try {
		var e = document.getElementsByClassName('rc-imageselect-error-select-more')
		if (e.length > 0) {
			few = e[0].style.display != 'none'
		}
		if (!few) {
			e = document.getElementsByClassName('rc-imageselect-error-select-one')
			if (e.length > 0 && e[0].style.display != 'none') {
				few = 'true'
			}
		}
		if (few) {
			e = document.getElementById('rc-imageselect-target')
			e = e && e.children && e.children.length && e.children[0]
			e = e && e.children && e.children.length && e.children[0]
			e = e && e.children && e.children.length && e
			for (var i = 0; e && i < e.children.length; i++) {
				var ei = e.children[i]
				ei = ei && ei.children && ei.children.length && ei
				for (var j = 0; ei && j < ei.children.length; j++) {
					var ej = ei.children[j]
					checked += ej && ej.className == 'rc-imageselect-tileselected' ? '1' : '0'
				}
			}
		}
	} finally {
		jsi.onCheckImageSelectedTooFew(workerUuid, few, checked)
	}
})

declareFrameFunction(1, 'recaptchaToogleImageChoice', function(index, sizeX) {
	var row = index / sizeX | 0
	var column = index % sizeX
	var tableObject = document.getElementById('rc-imageselect-target').children[0].children[0]
	recaptchaCommonEvent(tableObject.children[row].children[column].children[0], 'click')
})

var frameFunctionNames = ','
for (var i = 0; i < frameFunctions.length; i++) {
    if (frameFunctions[i].index == frameIndex) {
        frameFunctionNames += frameFunctions[i].name + ','
    }
}

function worker() {
    var events = jsi.takeEvents(workerUuid, frameFunctionNames)
    if (events) {
        events = JSON.parse(events)
        if (events.length && events.length > 0) {
            for (var i = 0; i < events.length; i++) {
                var event = events[i]
                var frameFunction = getFrameFunction(frameIndex, event.name)
                if (frameFunction) {
                    frameFunction.apply(this, event.args)
                }
            }
        }
    } else {
        clearInterval(injectWorkerInterval)
    }
}

var injectWorkerInterval = setInterval(worker, 250)
