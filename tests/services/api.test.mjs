import assert from 'node:assert/strict';
import {afterEach, test} from 'node:test';
import {get} from '../../src/services/api.js';

const originalFetch = globalThis.fetch;

afterEach(() => {
    globalThis.fetch = originalFetch;
});

test('uses the message from a JSON API error', async () => {
    globalThis.fetch = async () => new Response(JSON.stringify({message: 'MONDAY — Treadmill: Only elliptical intervals use cadence in RPM'}), {status: 400});

    await assert.rejects(get('/workout-plans/current'), {
        message: 'MONDAY — Treadmill: Only elliptical intervals use cadence in RPM'
    });
});

test('preserves plain-text API errors', async () => {
    globalThis.fetch = async () => new Response('The service is unavailable', {status: 503});

    await assert.rejects(get('/workout-plans/current'), {message: 'The service is unavailable'});
});

test('preserves network failures', async () => {
    globalThis.fetch = async () => { throw new TypeError('Network request failed'); };

    await assert.rejects(get('/workout-plans/current'), {message: 'Network request failed'});
});
