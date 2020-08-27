[(function (object) {
	window.grecaptcha = object
	window.hcaptcha = object
})({
	ready: function(callback) {
		callback()
	},
	render: function(container, params) {
		container = document.getElementById(container)
		if (container != null) {
			var element1 = document.createElement('textarea')
            element1.setAttribute('id', 'g-recaptcha-response')
            element1.setAttribute('name', 'g-recaptcha-response')
            element1.setAttribute('class', 'g-recaptcha-response')
            container.appendChild(element1)
			var element2 = document.createElement('textarea')
            element2.setAttribute('id', 'h-captcha-response')
            element2.setAttribute('name', 'h-captcha-response')
            element2.setAttribute('class', 'h-captcha-response')
            container.appendChild(element2)
		}
		var apiKey = params && params.sitekey;
		if (apiKey) {
			jsi.onRequestRecaptcha(apiKey)
		}
	},
	execute: function() {
		jsi.onRequestRecaptcha(null)
		return {
			then: function(callback) {
				window.jsiResultCallback = callback
			}
		}
	}
})]

function handleResult(result) {
	var element1 = document.getElementsByName('g-recaptcha-response')
	var element2 = document.getElementsByName('h-captcha-response')
	element1 = element1 && element1.length ? element1[0] : null
	element2 = element2 && element2.length ? element2[0] : null
	if (element1) {
		element1.value = result
	}
	if (element2) {
		element2.value = result
	}
	var callback = window.jsiResultCallback
	window.jsiResultCallback = null
	if (callback) {
		callback(result)
	} else {
		var element = element1 || element2
		if (element && element.form) {
			element.form.submit()
		}
	}
}

[(function (doOnLoad) {
	if (doOnLoad) {
		(function (callOnLoad) { callOnLoad() })(window[doOnLoad])
	}
})(__REPLACE_ON_LOAD__)]
