export interface Logger {
  info: (msg: string) => void
  warn: (msg: string) => void
  error: (error: Error) => void
}

let _logger: Logger = {
  info: () => {
  },
  warn: () => {
  },
  error: () => {
  },
}

export function setLogger(logger: Logger) {
  _logger = {
    info: (msg) => logger.info(`[GateGuard] ${msg}`),
    warn: (msg) => logger.warn(`[GateGuard] ${msg}`),
    error: (err) => logger.error(err),
  }
}

export function getLogger(): Logger {
  return _logger
}
