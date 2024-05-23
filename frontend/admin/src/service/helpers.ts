export function postJson(url: string, payload: any): Promise<Response> {
    const xsrfName = document.querySelector('meta[name=_csrf_header]')?.getAttribute('content') as string;
    const xsrfValue = document.querySelector('meta[name=_csrf]')?.getAttribute('content') as string;
    return fetch(url, {
        method: 'POST', credentials: 'include', headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            [xsrfName]: xsrfValue
        },
        body: JSON.stringify(payload)
    })
}