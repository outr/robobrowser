window.oc = window.console;      // Keep a reference to the original console
window.console = {};             // Replace the console with our new version
window.logs = [];                // Create a place to cache log records

window.console.clear = function() {
    window.logs = [];
    return window.oc.clear();
};
window.console.count = function(label) {
    return window.oc.count(label);
};
window.console.countReset = function(label) {
    return window.oc.countReset(label);
};
window.console.debug = function(message, ...arguments) {
    window.logs.push({'level': 'debug', 'message': message, 'timestamp': Date.now()});
    return window.oc.debug(message, ...arguments);
};
window.console.dir = function(object) {
    return window.oc.dir(object);
};
window.console.dirxml = function(object) {
    return window.oc.dirxml(object);
};
window.console.error = function(message, ...arguments) {
    window.logs.push({'level': 'info', 'message': message, 'timestamp': Date.now()});
    return window.oc.error(message, ...arguments);
};
window.console.group = function(label) {
    return window.oc.group(label);
};
window.console.groupCollapsed = function(label) {
    return window.oc.groupCollapsed(label);
};
window.console.groupEnd = function() {
    return window.oc.groupEnd();
};
window.console.info = function(message, ...arguments) {
    window.logs.push({'level': 'info', 'message': message, 'timestamp': Date.now()});
    return window.oc.info(message, ...arguments);
};
window.console.log = function(message, ...arguments) {
    window.logs.push({'level': 'info', 'message': message, 'timestamp': Date.now()});
    return window.oc.log(message, ...arguments);
};
window.console.table = function(data, columns) {
    return window.oc.table(data, columns);
};
window.console.time = function(label) {
    return window.oc.time(label);
};
window.console.timeEnd = function(label) {
    return window.oc.timeEnd(label);
};
window.console.timeLog = function(label) {
    return window.oc.timeLog(label);
};
window.console.trace = function(message, ...arguments) {
    window.logs.push({'level': 'trace', 'message': message, 'timestamp': Date.now()});
    return window.oc.trace(message, ...arguments);
};
window.console.warn = function(message, ...arguments) {
    window.logs.push({'level': 'warn', 'message': message, 'timestamp': Date.now()});
    return window.oc.warn(message, ...arguments);
};