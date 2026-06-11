import { Loader2 } from 'lucide-react'

interface LoadingProps {
  size?: 'sm' | 'md' | 'lg' | 'xl'
  text?: string
  fullScreen?: boolean
  centered?: boolean
}

const sizeClasses: Record<string, string> = {
  sm: 'w-4 h-4',
  md: 'w-6 h-6',
  lg: 'w-8 h-8',
  xl: 'w-12 h-12'
}

const textSizeClasses: Record<string, string> = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-lg',
  xl: 'text-xl'
}

function Loading({ size = 'md', text, fullScreen = false, centered = false }: LoadingProps): JSX.Element {
  const content = (
    <div className={`flex flex-col items-center gap-3 ${centered || fullScreen ? 'justify-center' : ''}`}>
      <Loader2 className={`${sizeClasses[size]} text-bronze-green animate-spin`} />
      {text && (
        <p className={`${textSizeClasses[size]} text-gray-400 font-medium`}>{text}</p>
      )}
    </div>
  )

  if (fullScreen) {
    return (
      <div className="fixed inset-0 bg-dark-500/90 backdrop-blur-sm flex items-center justify-center z-50">
        {content}
      </div>
    )
  }

  if (centered) {
    return (
      <div className="flex items-center justify-center w-full h-full min-h-[200px]">
        {content}
      </div>
    )
  }

  return content
}

export default Loading
