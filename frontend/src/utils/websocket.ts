import { io, Socket } from 'socket.io-client'
import type { RealTimeData, Alarm } from '@/types'

type MessageType = 'sensor_data' | 'alarm' | 'device_status' | 'analysis_progress'

interface WebSocketMessage<T = unknown> {
  type: MessageType
  data: T
  timestamp: string
}

type MessageHandler<T = unknown> = (data: T, timestamp: string) => void

class WebSocketClient {
  private socket: Socket | null = null
  private url: string
  private reconnectAttempts: number = 0
  private maxReconnectAttempts: number = 5
  private reconnectDelay: number = 3000
  private isManualDisconnect: boolean = false
  private handlers: Map<MessageType, Set<MessageHandler>> = new Map()

  constructor(url: string) {
    this.url = url
  }

  public connect(): void {
    if (this.socket?.connected) {
      return
    }

    this.isManualDisconnect = false
    this.socket = io(this.url, {
      transports: ['websocket', 'polling'],
      reconnection: false,
      timeout: 10000
    })

    this.socket.on('connect', () => {
      console.log('WebSocket connected')
      this.reconnectAttempts = 0
    })

    this.socket.on('disconnect', (reason) => {
      console.log('WebSocket disconnected:', reason)
      if (!this.isManualDisconnect) {
        this.attemptReconnect()
      }
    })

    this.socket.on('connect_error', (error) => {
      console.error('WebSocket connection error:', error)
      if (!this.isManualDisconnect) {
        this.attemptReconnect()
      }
    })

    this.socket.on('message', (message: WebSocketMessage) => {
      this.handleMessage(message)
    })

    this.socket.on('sensor_data', (data: RealTimeData) => {
      this.emit('sensor_data', data, new Date().toISOString())
    })

    this.socket.on('alarm', (data: Alarm) => {
      this.emit('alarm', data, new Date().toISOString())
    })
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnect attempts reached')
      return
    }

    this.reconnectAttempts++
    console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

    setTimeout(() => {
      if (!this.isManualDisconnect) {
        this.connect()
      }
    }, this.reconnectDelay)
  }

  private handleMessage(message: WebSocketMessage): void {
    this.emit(message.type, message.data, message.timestamp)
  }

  private emit(type: MessageType, data: unknown, timestamp: string): void {
    const handlers = this.handlers.get(type)
    if (handlers) {
      handlers.forEach((handler) => handler(data, timestamp))
    }
  }

  public on<T = unknown>(type: MessageType, handler: MessageHandler<T>): () => void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set())
    }
    this.handlers.get(type)!.add(handler as MessageHandler)

    return () => {
      this.off(type, handler)
    }
  }

  public off<T = unknown>(type: MessageType, handler: MessageHandler<T>): void {
    const handlers = this.handlers.get(type)
    if (handlers) {
      handlers.delete(handler as MessageHandler)
    }
  }

  public send(type: string, data: unknown): void {
    if (this.socket?.connected) {
      this.socket.emit(type, data)
    } else {
      console.error('WebSocket is not connected')
    }
  }

  public disconnect(): void {
    this.isManualDisconnect = true
    if (this.socket) {
      this.socket.disconnect()
      this.socket = null
    }
    this.reconnectAttempts = 0
  }

  public isConnected(): boolean {
    return this.socket?.connected ?? false
  }

  public joinRoom(roomId: string): void {
    this.send('join', roomId)
  }

  public leaveRoom(roomId: string): void {
    this.send('leave', roomId)
  }
}

const websocket = new WebSocketClient('ws://localhost:8080/ws')

export default websocket
