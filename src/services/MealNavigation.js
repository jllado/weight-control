export function afterLogin(query) {
    if (typeof query.mealReturn === 'string' && /^\/(?:meals\/(?:new|\d+\/edit)|dishes\/\d+\/edit)(?:\?|$)/.test(query.mealReturn)) return query.mealReturn;
    return {path: '/', query};
}

export function loginQuery(route) {
    return (route.path.startsWith('/meals/') || route.path.startsWith('/dishes/')) ? {...route.query, mealReturn: route.fullPath} : route.query;
}
