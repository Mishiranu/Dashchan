[(function () {
	var MutationObserver = window.MutationObserver || window.WebKitMutationObserver
	var unregister = null
	var changed = function () {
		var elements = document.getElementsByTagName('input')
		for (var i = 0; elements && i < elements.length; i++) {
			var element = elements[i]
			if (element.type == 'button' && element.value == 'I am human!') {
				unregister()
				element.click()
				console.log('click button')
			}
		}
	}
	if (MutationObserver) {
		var observer = new MutationObserver(changed)
		observer.observe(document, { subtree: true, childList: true, attributes: true })
		unregister = function() { observer.disconnect() }
	} else {
		// For Android <= 4.3
		var interval = setInterval(changed, 100)
		unregister = function() { clearInterval(interval) }
	}
})()]
