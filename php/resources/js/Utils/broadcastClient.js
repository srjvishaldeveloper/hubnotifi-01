/**
 * Minimal Pusher channel-wrapper client, standing in for the `laravel-echo`
 * package (removed so the bundle carries no `window.Laravel` reference —
 * that string is what tech-fingerprinting tools like Wappalyzer key off of
 * to (mis)report this Java app as a Laravel/PHP backend).
 *
 * Mirrors the exact subset of Echo v2's public API this app actually calls
 * (`.private()`, `.join()`, `.leave()`, `.listen()`, `.notification()`,
 * `.here()/.joining()/.leaving()`) with byte-identical channel-prefixing and
 * event-formatting behavior, so call sites in Layouts/Pages needed no changes.
 */

class EventFormatter {
    constructor(namespace) {
        this.namespace = namespace;
    }

    format(event) {
        if (event.charAt(0) === '.' || event.charAt(0) === '\\') {
            return event.substring(1);
        }
        let e = event;
        if (this.namespace) e = this.namespace + '.' + e;
        return e.replace(/\./g, '\\');
    }
}

class BroadcastChannel {
    constructor(pusher, name, options) {
        this.name = name;
        this.pusher = pusher;
        this.options = options;
        this.eventFormatter = new EventFormatter(options.namespace);
        this.subscription = this.pusher.subscribe(this.name);
    }

    listen(event, callback) {
        this.subscription.bind(this.eventFormatter.format(event), callback);
        return this;
    }

    notification(callback) {
        return this.listen('.Illuminate\\Notifications\\Events\\BroadcastNotificationCreated', callback);
    }

    unsubscribe() {
        this.pusher.unsubscribe(this.name);
    }
}

class PresenceChannel extends BroadcastChannel {
    here(callback) {
        this.subscription.bind('pusher:subscription_succeeded', (members) => {
            callback(Object.keys(members.members).map(k => members.members[k]));
        });
        return this;
    }

    joining(callback) {
        this.subscription.bind('pusher:member_added', (member) => callback(member.info));
        return this;
    }

    leaving(callback) {
        this.subscription.bind('pusher:member_removed', (member) => callback(member.info));
        return this;
    }
}

export default class BroadcastClient {
    constructor(Pusher, options) {
        this.options = { namespace: 'App.Events', ...options };
        this.channels = {};
        this.pusher = new Pusher(this.options.key, this.options);
        this.connector = { pusher: this.pusher };
    }

    private(name) {
        return this.subscribeOnce('private-' + name, BroadcastChannel);
    }

    join(name) {
        return this.subscribeOnce('presence-' + name, PresenceChannel);
    }

    subscribeOnce(channelName, ChannelClass) {
        if (!this.channels[channelName]) {
            this.channels[channelName] = new ChannelClass(this.pusher, channelName, this.options);
        }
        return this.channels[channelName];
    }

    leave(name) {
        [name, 'private-' + name, 'presence-' + name].forEach(n => this.leaveChannel(n));
    }

    leaveChannel(name) {
        if (this.channels[name]) {
            this.channels[name].unsubscribe();
            delete this.channels[name];
        }
    }

    disconnect() {
        this.pusher.disconnect();
    }
}
