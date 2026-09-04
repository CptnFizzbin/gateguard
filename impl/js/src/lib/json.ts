export type JsonValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | JsonArray
  | JsonObject

export type JsonArray = JsonValue[]

export type JsonObject = {
  [key: string | number]: JsonValue
}
