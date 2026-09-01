declare const ActionBrand: unique symbol;

export type Action<T extends string = string> = T & { readonly [ActionBrand]?: T };
