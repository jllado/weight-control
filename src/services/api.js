async function request(path, options = {}) {
    const response = await fetch(`/api${path}`, {
        credentials: 'include',
        ...options,
        headers: {
            ...(options.body instanceof FormData ? {} : {'Content-Type': 'application/json'}),
            ...(options.headers || {})
        }
    });

    if (response.status === 204) {
        return undefined;
    }

    if (!response.ok) {
        const text = await response.text();
        let message = text || response.statusText;
        try {
            const error = JSON.parse(text);
            if (typeof error.message === 'string' && error.message.trim()) message = error.message;
        } catch {
            // Plain-text error responses remain readable as-is.
        }
        throw new Error(message);
    }

    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }

    return response.text();
}

export function get(path) {
    return request(path);
}

export function post(path, body) {
    return request(path, {
        method: 'POST',
        body: body instanceof FormData ? body : JSON.stringify(body)
    });
}

export function put(path, body) {
    return request(path, {
        method: 'PUT',
        body: JSON.stringify(body)
    });
}

export function del(path, body) {
    return request(path, {
        method: 'DELETE',
        ...(body === undefined ? {} : {body: JSON.stringify(body)})
    });
}
