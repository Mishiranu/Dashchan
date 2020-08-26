window.grecaptcha = {
	ready: function(callback) {
		callback()
	},
	execute: function() {
		jsi.onRequestRecaptcha()
		return {
			then: function(callback) {
				window.jsiResultCallback = callback
			}
		}
	}
}

function handleResult(result) {
	var callback = window.jsiResultCallback
	window.jsiResultCallback = null
	if (callback) {
		callback(result)
	}
}
