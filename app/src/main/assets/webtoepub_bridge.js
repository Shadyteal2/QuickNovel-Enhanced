"use strict";

// Mock Chrome/Browser extensions namespaces
window.chrome = window.chrome || {};
window.chrome.i18n = window.chrome.i18n || {
    getMessage(key, placeholders) {
        return key;
    }
};
window.chrome.runtime = window.chrome.runtime || {
    getManifest() {
        return { version: "1.0.0" };
    }
};
window.browser = window.chrome;

// Mock WebToEpub UI elements and options checkboxes
const originalGetElementById = document.getElementById;
document.getElementById = function(id) {
    let el = originalGetElementById.call(document, id);
    if (el) return el;
    
    const stubIds = [
        "removeChapterNumberCheckbox",
        "selectRetryLongerCheckbox",
        "coverFromUrlCheckboxInput",
        "lesstagsCheckbox"
    ];
    if (stubIds.includes(id) || id.toLowerCase().includes("checkbox") || id.toLowerCase().includes("input") || id.toLowerCase().includes("select") || id.toLowerCase().includes("row")) {
        return {
            checked: false,
            value: "",
            hidden: false,
            style: {},
            addEventListener: () => {},
            removeEventListener: () => {}
        };
    }
    return null;
};

const originalQuerySelector = document.querySelector;
document.querySelector = function(selector) {
    let el = originalQuerySelector.call(document, selector);
    if (el) return el;
    
    if (selector.startsWith("#")) {
        const id = selector.substring(1);
        const stubIds = [
            "removeChapterNumberCheckbox",
            "selectRetryLongerCheckbox",
            "coverFromUrlCheckboxInput",
            "lesstagsCheckbox"
        ];
        if (stubIds.includes(id) || id.toLowerCase().includes("checkbox") || id.toLowerCase().includes("input") || id.toLowerCase().includes("select") || id.toLowerCase().includes("row")) {
            return {
                checked: false,
                value: "",
                hidden: false,
                style: {},
                addEventListener: () => {},
                removeEventListener: () => {}
            };
        }
    }
    return null;
};

// Mock WebToEpub UI elements
window.ErrorLog = {
    showErrorMessage(msg) {
        console.error("WebToEpub Error: " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.onError(msg.toString());
        }
    },
    log(err) {
        console.error("WebToEpub Log: ", err);
    }
};

window.ProgressBar = {
    setMax(val) {},
    setValue(val) {},
    updateValue(val) {}
};

window.CoverImageUI = {
    showCoverImageUrlInput(val) {},
    setCoverImageUrl(url) {},
    getCoverImageUrl() { return null; }
};

window.ChapterUrlsUI = {
    showDownloadState(row, state) {},
    showTocProgress(chapters) {},
    populateChapterUrlsTable(chapters) {},
    connectButtonHandlers() {}
};

// Implement wrapFetch using browser native fetch and DOMParser
window.HttpClient = {
    async wrapFetch(url, options) {
        try {
            let isCurrentUrl = (url === window.location.href || 
                                url === document.baseURI || 
                                url.replace(/\/$/, "") === window.location.href.replace(/\/$/, ""));
            if (isCurrentUrl) {
                return {
                    responseXML: document,
                    text: document.documentElement.outerHTML
                };
            }

            let makeTextDecoder = options?.makeTextDecoder || (() => new TextDecoder("utf-8"));
            
            // Perform native fetch
            let resp = await fetch(url);
            if (!resp.ok) {
                throw new Error("HTTP error " + resp.status + " loading " + url);
            }
            
            let buffer = await resp.arrayBuffer();
            let decoder = makeTextDecoder();
            let text = decoder.decode(buffer);
            
            let parser = new DOMParser();
            let doc = parser.parseFromString(text, "text/html");
            
            // Set base URL tag so relative links resolve correctly
            let baseTag = doc.querySelector("base");
            if (!baseTag) {
                let newBase = doc.createElement("base");
                newBase.setAttribute("href", url);
                doc.head.appendChild(newBase);
            }
            
            return {
                responseXML: doc,
                text: text
            };
        } catch (e) {
            console.error("Error in HttpClient.wrapFetch: ", e);
            throw e;
        }
    },

    async fetchJson(url, fetchOptions) {
        try {
            let resp = await fetch(url, fetchOptions);
            if (!resp.ok) {
                throw new Error("HTTP error " + resp.status + " loading " + url);
            }
            let data = await resp.json();
            return {
                json: data
            };
        } catch (e) {
            console.error("Error in HttpClient.fetchJson: ", e);
            throw e;
        }
    },

    async fetchHtml(url) {
        let res = await this.wrapFetch(url);
        return res;
    },

    async fetchText(url) {
        try {
            let isCurrentUrl = (url === window.location.href || 
                                url === document.baseURI || 
                                url.replace(/\/$/, "") === window.location.href.replace(/\/$/, ""));
            if (isCurrentUrl) {
                return document.documentElement.outerHTML;
            }

            let resp = await fetch(url);
            if (!resp.ok) {
                throw new Error("HTTP error " + resp.status + " loading " + url);
            }
            let text = await resp.text();
            return text;
        } catch (e) {
            console.error("Error in HttpClient.fetchText: ", e);
            throw e;
        }
    }
};
