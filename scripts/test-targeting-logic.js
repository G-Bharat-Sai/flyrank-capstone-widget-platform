const assert = require("assert");

function pageMatchesTargets(targetPages, path) {
    if (!targetPages || !targetPages.length) return true;
    for (let i = 0; i < targetPages.length; i++) {
        const pattern = targetPages[i];
        const starIndex = pattern.indexOf("*");
        if (starIndex === -1) {
            if (path === pattern) return true;
        } else {
            const prefix = pattern.slice(0, starIndex);
            if (path.indexOf(prefix) === 0) return true;
        }
    }
    return false;
}

function makeMemoryStorage() {
    const store = {};
    return {
        getItem: function (key) {
            return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
        },
        setItem: function (key, value) {
            store[key] = value;
        }
    };
}

function hasBeenSeen(storage, widgetId) {
    try {
        return storage.getItem("widget_seen_" + widgetId) === "1";
    } catch (e) {
        return false;
    }
}

function markAsSeen(storage, widgetId) {
    try {
        storage.setItem("widget_seen_" + widgetId, "1");
    } catch (e) {
        // ignore storage errors, mirrors the widget script fail-open behavior
    }
}

// targetPages: page-matching logic
assert.strictEqual(pageMatchesTargets(undefined, "/anything"), true, "no targetPages means show everywhere");
assert.strictEqual(pageMatchesTargets([], "/anything"), true, "empty targetPages means show everywhere");
assert.strictEqual(pageMatchesTargets(["/pricing"], "/pricing"), true, "exact match");
assert.strictEqual(pageMatchesTargets(["/pricing"], "/pricing/enterprise"), false, "exact pattern does not match a sub-path");
assert.strictEqual(pageMatchesTargets(["/pricing*"], "/pricing/enterprise"), true, "wildcard prefix matches sub-path");
assert.strictEqual(pageMatchesTargets(["/blog*"], "/pricing"), false, "wildcard prefix does not match an unrelated path");
assert.strictEqual(pageMatchesTargets(["/a", "/b*"], "/b/anything"), true, "matches the second pattern in a list");

// oncePerVisitor: seen-state logic, against an in-memory getItem/setItem stand-in
const storage = makeMemoryStorage();
assert.strictEqual(hasBeenSeen(storage, "widget-1"), false, "not seen before the first visit");
markAsSeen(storage, "widget-1");
assert.strictEqual(hasBeenSeen(storage, "widget-1"), true, "seen after being marked");
assert.strictEqual(hasBeenSeen(storage, "widget-2"), false, "a different widget id is unaffected");

console.log("All targeting-rule logic assertions passed.");