const version = '2026-07-28';
const serverInfo = { name: 'weight-control-coach', version: '1.0.0' };
const privateHeaders = { 'Cache-Control': 'no-store' };

function reply(id, result) {
    return Response.json({
        jsonrpc: '2.0', id,
        result: { resultType: 'complete', _meta: { 'io.modelcontextprotocol/serverInfo': serverInfo }, ...result }
    }, { headers: privateHeaders });
}

function rpcError(id, code, message, data) {
    return Response.json({ jsonrpc: '2.0', id, error: { code, message, data } }, { headers: privateHeaders });
}

function textResult(text, isError = false) {
    return { content: [{ type: 'text', text }], isError };
}

async function photoResult(origin, urls, sides) {
    const content = [];
    const orderedSides = ['FRONT', 'LEFT', 'RIGHT'].filter(side => sides.includes(side));
    if (urls.length !== orderedSides.length) return textResult('The requested photo set was incomplete.', true);
    for (const [index, signedUrl] of urls.entries()) {
        const url = new URL(signedUrl);
        if (url.origin !== origin || !url.pathname.startsWith('/api/chatgpt-files/progress-photos/')) {
            return textResult('Weight Control returned an invalid photo link.', true);
        }
        const response = await fetch(url, { redirect: 'manual' });
        const mimeType = response.headers.get('Content-Type')?.split(';')[0];
        if (!response.ok || !mimeType?.startsWith('image/')) return textResult('Unable to retrieve the selected progress photo.', true);
        const bytes = new Uint8Array(await response.arrayBuffer());
        let binary = '';
        for (let offset = 0; offset < bytes.length; offset += 8192) {
            binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
        }
        content.push({ type: 'text', text: orderedSides[index] }, { type: 'image', data: btoa(binary), mimeType });
    }
    return { content, isError: false };
}

async function callTool(baseUrl, operation, args, token, validate) {
    if (!validate(args)) {
        const { instanceLocation, keywordLocation } = validate.errors[0];
        return textResult('Invalid tool input at ' + instanceLocation + ' (' + keywordLocation + ').', true);
    }
    if (!token) return textResult('The Weight Control API credential is missing.', true);
    const path = operation.path.replace(/\{(\w+)\}/g, (_, name) => encodeURIComponent(args[name]));
    const url = new URL(baseUrl + path);
    for (const name of operation.query) {
        const value = args[name] ?? operation.tool.inputSchema.properties[name].default;
        if (value !== undefined) url.searchParams.set(name, Array.isArray(value) ? value.join(',') : value);
    }
    const init = { method: operation.method, headers: { Authorization: 'Bearer ' + token, Accept: 'application/json' }, redirect: 'manual' };
    if (operation.body.length) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(Object.fromEntries(operation.body.filter(key => Object.hasOwn(args, key)).map(key => [key, args[key]])));
    }
    try {
        const response = await fetch(url, init);
        if (!response.ok) {
            let message = 'Weight Control returned HTTP ' + response.status + '.';
            if ([400, 409].includes(response.status)) {
                const validation = await response.json().catch(() => null);
                if (typeof validation?.message === 'string') message += ' ' + validation.message;
            }
            return textResult(message, true);
        }
        if (response.status === 204 && operation.allowsNoContent) return textResult('null');
        const body = await response.text();
        const data = JSON.parse(body);
        if (operation.method !== 'GET' && (data === null || typeof data !== 'object' || Array.isArray(data))) {
            return textResult('The save returned no record. Read the saved state before retrying.', true);
        }
        if (operation.tool.name === 'getProgressPhotoFiles') {
            return await photoResult(url.origin, data.openaiFileResponse, args.sides);
        }
        return textResult(body);
    } catch {
        return textResult('Could not verify the result with Weight Control. Read the saved state before retrying a write.', true);
    }
}

function createWorker({ baseUrl, operations, instructions }, validators) {
    const tools = operations.map(operation => operation.tool);
    const byName = new Map(operations.map(operation => [operation.tool.name, operation]));
    return {
        async fetch(request, env) {
            const url = new URL(request.url);
            if (url.pathname === '/' && request.method === 'GET') {
                return new Response('Weight Control Coach\n\nMention @Weight Control Coach in a ChatGPT conversation.');
            }
            if (url.pathname !== '/mcp') return new Response(null, { status: 404 });
            if (request.method !== 'POST') return new Response(null, { status: 405, headers: { Allow: 'POST' } });
            const origin = request.headers.get('Origin');
            if (origin && origin !== url.origin) return new Response(null, { status: 403 });
            if (request.headers.get('Content-Type')?.split(';')[0].trim() !== 'application/json') return new Response(null, { status: 415 });
            let message;
            try {
                message = await request.json();
            } catch {
                return rpcError(null, -32700, 'Invalid JSON.');
            }
            if (message?.jsonrpc !== '2.0' || !['string', 'number'].includes(typeof message.id)) {
                return rpcError(null, -32600, 'Expected a JSON-RPC request.');
            }
            const { id, method, params } = message;
            const protocol = params?._meta?.['io.modelcontextprotocol/protocolVersion'] ?? request.headers.get('MCP-Protocol-Version');
            if (protocol && protocol !== version) {
                return rpcError(id, -32022, 'Unsupported protocol version.', { supported: [version], requested: protocol });
            }
            if (method === 'server/discover') return reply(id, { supportedVersions: [version], capabilities: { tools: {} }, instructions });
            if (method === 'tools/list') return reply(id, { tools });
            if (method === 'ping') return reply(id, {});
            if (method !== 'tools/call') return rpcError(id, -32601, 'Unknown method.');
            const email = request.headers.get('oai-authenticated-user-email');
            if (!request.headers.get('oai-authenticated-user-id') || !email || email.toLowerCase() !== env.COACH_OWNER_EMAIL?.toLowerCase()) {
                return new Response(null, { status: 403, headers: privateHeaders });
            }
            const operation = byName.get(params?.name);
            if (!operation) return rpcError(id, -32602, 'Unknown tool.');
            return reply(id, await callTool(baseUrl, operation, params.arguments ?? {}, env.CHATGPT_ACTION_TOKEN, validators[operation.tool.name]));
        }
    };
}
