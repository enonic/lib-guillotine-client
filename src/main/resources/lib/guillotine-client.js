/* global __ */

const executor = __.newBean('com.enonic.lib.guillotine.GuillotineExecutorBean');

/**
 * Executes a GraphQL query against the Guillotine engine, in-process via OSGi (no HTTP round-trip).
 * The query runs in the current XP context, so wrap the call in `contextLib.run({...}, ...)` to target
 * a specific project/branch/principals.
 *
 * @param {object} params Parameters.
 * @param {string} params.query GraphQL query.
 * @param {object} [params.variables] GraphQL query variables.
 * @returns {object} GraphQL-spec response object, e.g. `{ data: {...} }` or `{ errors: [...] }`.
 */
exports.query = function (params) {
    if (!params || !params.query) {
        throw new Error("Parameter 'query' is required");
    }
    const result = executor.execute(params.query, __.toScriptValue(params.variables));
    return __.toNativeObject(result);
};
