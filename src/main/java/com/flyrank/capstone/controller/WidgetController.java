package com.flyrank.capstone.controller;
import com.flyrank.capstone.dto.CreateWidgetRequest;
import com.flyrank.capstone.dto.WidgetConfigResponse;
import com.flyrank.capstone.dto.WidgetResponse;
import com.flyrank.capstone.entity.Widget;
import com.flyrank.capstone.service.WidgetService;
import com.flyrank.capstone.util.WidgetJsConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/widgets")
@RequiredArgsConstructor
public class WidgetController {
    private static final String WIDGET_JS_CONTENT = """
            (function() {
                var script = document.currentScript;
                var widgetId = script.getAttribute('data-widget-id');
                var baseUrl = script.src.split('/widgets/')[0];
                fetch(baseUrl + '/widgets/' + widgetId + '/config')
                    .then(function(res) { return res.json(); })
                    .then(function(config) {
                        maybeRenderWidget(script, widgetId, baseUrl, config);
                    })
                    .catch(function(err) {
                        console.error('FlyRank widget failed to load:', err);
                    });
                function pageMatchesTargets(targetPages, path) {
        if (!targetPages || !targetPages.length) return true;
        for (var i = 0; i < targetPages.length; i++) {
            var pattern = targetPages[i];
            var starIndex = pattern.indexOf('*');
            if (starIndex === -1) {
                if (path === pattern) return true;
            } else {
                var prefix = pattern.slice(0, starIndex);
                if (path.indexOf(prefix) === 0) return true;
            }
        }
        return false;
    }
    function hasBeenSeen(widgetId) {
        try {
            return localStorage.getItem('widget_seen_' + widgetId) === '1';
        } catch (e) {
            return false;
        }
    }
    function markAsSeen(widgetId) {
        try {
            localStorage.setItem('widget_seen_' + widgetId, '1');
        } catch (e) {
        }
    }
    function maybeRenderWidget(script, widgetId, baseUrl, config) {
        var opts = config.displayOptions || {};
        if (!pageMatchesTargets(opts.targetPages, window.location.pathname)) {
            return;
        }
        if (opts.oncePerVisitor && hasBeenSeen(widgetId)) {
            return;
        }
        if (opts.oncePerVisitor) {
            markAsSeen(widgetId);
        }
        var delayMs = (Number(opts.delaySeconds) || 0) * 1000;
        if (delayMs > 0) {
            setTimeout(function() {
                renderWidget(script, widgetId, baseUrl, config);
            }, delayMs);
        } else {
            renderWidget(script, widgetId, baseUrl, config);
        }
    }
    function sha256Hex(str) {
                    var enc = new TextEncoder();
                    return crypto.subtle.digest('SHA-256', enc.encode(str)).then(function(buf) {
                        var bytes = new Uint8Array(buf);
                        var hex = '';
                        for (var i = 0; i < bytes.length; i++) {
                            var h = bytes[i].toString(16);
                            if (h.length < 2) h = '0' + h;
                            hex += h;
                        }
                        return hex;
                    });
                }
                function solveChallenge(seed, difficulty) {
                    var prefix = '';
                    for (var i = 0; i < difficulty; i++) { prefix += '0'; }
                    var attempt = 0;
                    function tryNext() {
                        var nonce = String(attempt);
                        return sha256Hex(seed + nonce).then(function(hash) {
                            if (hash.indexOf(prefix) === 0) {
                                return nonce;
                            }
                            attempt++;
                            return tryNext();
                        });
                    }
                    return tryNext();
                }
                function renderWidget(script, widgetId, baseUrl, config) {
                    var renderedAt = Date.now();
                    var challengePromise = null;
                    if (config.requireProofOfWork) {
                        challengePromise = fetch(baseUrl + '/submissions/challenge')
                            .then(function(res) { return res.json(); })
                            .then(function(challenge) {
                                return solveChallenge(challenge.seed, challenge.difficulty).then(function(nonce) {
                                    return { challengeId: challenge.challengeId, nonce: nonce };
                                });
                            });
                    }
                    var container = document.createElement('div');
                    container.className = 'flyrank-widget';
                    container.style.cssText = 'max-width:400px;font-family:sans-serif;border:1px solid #ddd;border-radius:8px;padding:16px;';
                    var title = document.createElement('h3');
                    title.textContent = config.title;
                    container.appendChild(title);
                    if (config.description) {
                        var desc = document.createElement('p');
                        desc.textContent = config.description;
                        container.appendChild(desc);
                    }
                    var form = document.createElement('form');
                    (config.fields || []).forEach(function(field) {
                        var label = document.createElement('label');
                        label.textContent = field.label;
                        label.style.cssText = 'display:block;margin-top:8px;';
                        var input = document.createElement('input');
                        input.type = field.type || 'text';
                        input.name = field.name;
                        if (field.required) input.required = true;
                        input.style.cssText = 'display:block;width:100%;padding:6px;margin-top:4px;box-sizing:border-box;';
                        label.appendChild(input);
                        form.appendChild(label);
                    });
                    var honeypot = document.createElement('input');
                    honeypot.type = 'text';
                    honeypot.name = '_hp';
                    honeypot.style.cssText = 'position:absolute;left:-9999px;';
                    honeypot.tabIndex = -1;
                    honeypot.autocomplete = 'off';
                    form.appendChild(honeypot);
                    var submitBtn = document.createElement('button');
                    submitBtn.type = 'submit';
                    submitBtn.textContent = config.buttonText || 'Submit';
                    submitBtn.style.cssText = 'margin-top:12px;padding:8px 16px;';
                    form.appendChild(submitBtn);
                    var message = document.createElement('div');
                    message.style.cssText = 'margin-top:8px;';
                    form.addEventListener('submit', function(e) {
                        e.preventDefault();
                        var fields = {};
                        (config.fields || []).forEach(function(field) {
                            fields[field.name] = form.elements[field.name].value;
                        });
                        var payload = {
                            widgetId: widgetId,
                            fields: fields,
                            honeypot: honeypot.value,
                            formRenderedAt: renderedAt
                        };
                        var readyToSend = challengePromise
                            ? challengePromise.then(function(solved) {
                                payload.challengeId = solved.challengeId;
                                payload.challengeNonce = solved.nonce;
                            })
                            : Promise.resolve();
                        readyToSend.then(function() {
                            return fetch(baseUrl + '/submissions', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                        })
                        .then(function(res) {
                            if (res.ok) {
                                message.textContent = 'Thank you!';
                                message.style.color = 'green';
                                form.reset();
                            } else {
                                return res.json().then(function(err) {
                                    message.textContent = err.message || 'Something went wrong.';
                                    message.style.color = 'red';
                                });
                            }
                        })
                        .catch(function() {
                            message.textContent = 'Network error, please try again.';
                            message.style.color = 'red';
                        });
                    });
                    container.appendChild(form);
                    container.appendChild(message);
                    script.parentNode.insertBefore(container, script.nextSibling);
                }
            })();
            """;
    private static final String WIDGET_JS_CONTENT_MINIFIED = "(function(){var script=document.currentScript;var widgetId=script.getAttribute(\"data-widget-id\");var baseUrl=script.src.split(\"/widgets/\")[0];fetch(baseUrl+\"/widgets/\"+widgetId+\"/config\").then(function(res){return res.json()}).then(function(config){maybeRenderWidget(script,widgetId,baseUrl,config)}).catch(function(err){console.error(\"FlyRank widget failed to load:\",err)});function pageMatchesTargets(targetPages,path){if(!targetPages||!targetPages.length)return true;for(var i=0;i<targetPages.length;i++){var pattern=targetPages[i];var starIndex=pattern.indexOf(\"*\");if(starIndex===-1){if(path===pattern)return true}else{var prefix=pattern.slice(0,starIndex);if(path.indexOf(prefix)===0)return true}}return false}function hasBeenSeen(widgetId){try{return localStorage.getItem(\"widget_seen_\"+widgetId)===\"1\"}catch(e){return false}}function markAsSeen(widgetId){try{localStorage.setItem(\"widget_seen_\"+widgetId,\"1\")}catch(e){}}function maybeRenderWidget(script,widgetId,baseUrl,config){var opts=config.displayOptions||{};if(!pageMatchesTargets(opts.targetPages,window.location.pathname)){return}if(opts.oncePerVisitor&&hasBeenSeen(widgetId)){return}if(opts.oncePerVisitor){markAsSeen(widgetId)}var delayMs=(Number(opts.delaySeconds)||0)*1e3;if(delayMs>0){setTimeout(function(){renderWidget(script,widgetId,baseUrl,config)},delayMs)}else{renderWidget(script,widgetId,baseUrl,config)}}function sha256Hex(str){var enc=new TextEncoder;return crypto.subtle.digest(\"SHA-256\",enc.encode(str)).then(function(buf){var bytes=new Uint8Array(buf);var hex=\"\";for(var i=0;i<bytes.length;i++){var h=bytes[i].toString(16);if(h.length<2)h=\"0\"+h;hex+=h}return hex})}function solveChallenge(seed,difficulty){var prefix=\"\";for(var i=0;i<difficulty;i++){prefix+=\"0\"}var attempt=0;function tryNext(){var nonce=String(attempt);return sha256Hex(seed+nonce).then(function(hash){if(hash.indexOf(prefix)===0){return nonce}attempt++;return tryNext()})}return tryNext()}function renderWidget(script,widgetId,baseUrl,config){var renderedAt=Date.now();var challengePromise=null;if(config.requireProofOfWork){challengePromise=fetch(baseUrl+\"/submissions/challenge\").then(function(res){return res.json()}).then(function(challenge){return solveChallenge(challenge.seed,challenge.difficulty).then(function(nonce){return{challengeId:challenge.challengeId,nonce:nonce}})})}var container=document.createElement(\"div\");container.className=\"flyrank-widget\";container.style.cssText=\"max-width:400px;font-family:sans-serif;border:1px solid #ddd;border-radius:8px;padding:16px;\";var title=document.createElement(\"h3\");title.textContent=config.title;container.appendChild(title);if(config.description){var desc=document.createElement(\"p\");desc.textContent=config.description;container.appendChild(desc)}var form=document.createElement(\"form\");(config.fields||[]).forEach(function(field){var label=document.createElement(\"label\");label.textContent=field.label;label.style.cssText=\"display:block;margin-top:8px;\";var input=document.createElement(\"input\");input.type=field.type||\"text\";input.name=field.name;if(field.required)input.required=true;input.style.cssText=\"display:block;width:100%;padding:6px;margin-top:4px;box-sizing:border-box;\";label.appendChild(input);form.appendChild(label)});var honeypot=document.createElement(\"input\");honeypot.type=\"text\";honeypot.name=\"_hp\";honeypot.style.cssText=\"position:absolute;left:-9999px;\";honeypot.tabIndex=-1;honeypot.autocomplete=\"off\";form.appendChild(honeypot);var submitBtn=document.createElement(\"button\");submitBtn.type=\"submit\";submitBtn.textContent=config.buttonText||\"Submit\";submitBtn.style.cssText=\"margin-top:12px;padding:8px 16px;\";form.appendChild(submitBtn);var message=document.createElement(\"div\");message.style.cssText=\"margin-top:8px;\";form.addEventListener(\"submit\",function(e){e.preventDefault();var fields={};(config.fields||[]).forEach(function(field){fields[field.name]=form.elements[field.name].value});var payload={widgetId:widgetId,fields:fields,honeypot:honeypot.value,formRenderedAt:renderedAt};var readyToSend=challengePromise?challengePromise.then(function(solved){payload.challengeId=solved.challengeId;payload.challengeNonce=solved.nonce}):Promise.resolve();readyToSend.then(function(){return fetch(baseUrl+\"/submissions\",{method:\"POST\",headers:{\"Content-Type\":\"application/json\"},body:JSON.stringify(payload)})}).then(function(res){if(res.ok){message.textContent=\"Thank you!\";message.style.color=\"green\";form.reset()}else{return res.json().then(function(err){message.textContent=err.message||\"Something went wrong.\";message.style.color=\"red\"})}}).catch(function(){message.textContent=\"Network error, please try again.\";message.style.color=\"red\"})});container.appendChild(form);container.appendChild(message);script.parentNode.insertBefore(container,script.nextSibling)}})();";
    private final WidgetService widgetService;
    @PostMapping
    public ResponseEntity<WidgetResponse> create(@Valid @RequestBody CreateWidgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(widgetService.create(request));
    }
    @GetMapping
    public ResponseEntity<List<WidgetResponse>> listMine() {
        return ResponseEntity.ok(widgetService.listForCurrentOwner());
    }
    @GetMapping("/{id}")
    public ResponseEntity<WidgetResponse> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(widgetService.getOne(id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<WidgetResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateWidgetRequest request) {
        return ResponseEntity.ok(widgetService.update(id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        widgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/config")
    public ResponseEntity<WidgetConfigResponse> getConfig(@PathVariable UUID id, WebRequest webRequest) {
        Widget widget = widgetService.getPublicWidget(id);
        String etag = "\"" + widget.getVersion() + "\"";
        if (webRequest.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(widgetService.toConfigResponse(widget));
    }
    @GetMapping(value = "/{id}/widget.js", produces = "application/javascript")
    public ResponseEntity<String> getWidgetScript(@PathVariable UUID id, WebRequest webRequest) {
        widgetService.getPublicWidget(id);
        String etag = "\"" + WidgetJsConstants.CURRENT_VERSION + "\"";
        if (webRequest.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .body(WIDGET_JS_CONTENT);
    }
    @GetMapping(value = "/{id}/widget.v{version}.js", produces = "application/javascript")
    public ResponseEntity<String> getVersionedWidgetScript(@PathVariable UUID id, @PathVariable String version, WebRequest webRequest) {
        widgetService.getPublicWidget(id);
        if (!WidgetJsConstants.CURRENT_VERSION.equals(version)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String etag = "\"" + WidgetJsConstants.CURRENT_VERSION + "\"";
        if (webRequest.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(WIDGET_JS_CONTENT_MINIFIED);
    }
}
