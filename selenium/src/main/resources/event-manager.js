window.roboEvents = {};
window.roboEvents.queue = [];
window.roboEvents.serverURL = null;
window.roboEvents.enqueueString = function(key, content, element) {
    window.roboEvents.queue.push({
        'key': key,
        'content': content,
        'element': element
    });
    if (window.roboEvents.serverURL != null) {
        fetch(window.roboEvents.serverURL, {
            method: 'get'
        });
    }
};
window.roboEvents.enqueueJson = function(key, json, element) {
    window.roboEvents.enqueueString(key, JSON.stringify(json), element);
};
window.roboEvents.get = function() {
    let events = window.roboEvents.queue;
    window.roboEvents.queue = [];
    return events;
};