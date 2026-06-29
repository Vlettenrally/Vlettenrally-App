import { useState } from 'react';
import { Box, CardMedia, CircularProgress } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';

interface PictureImageProps {
  src: string | null | undefined;
  alt: string;
  onClick?: () => void;
  sx?: SxProps<Theme>;
}

export default function PictureImage({ src, alt, onClick, sx }: PictureImageProps) {
  const [loaded, setLoaded] = useState(false);

  return (
    <Box
      sx={{
        aspectRatio: '3 / 2',
        position: 'relative',
        bgcolor: 'action.hover',
        overflow: 'hidden',
        cursor: src && onClick ? 'pointer' : 'default',
        ...sx,
      }}
      onClick={src && onClick ? onClick : undefined}
    >
      {!loaded && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <CircularProgress size={24} />
        </Box>
      )}
      {src && (
        <CardMedia
          component="img"
          image={src}
          alt={alt}
          onLoad={() => setLoaded(true)}
          sx={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            display: loaded ? 'block' : 'none',
          }}
        />
      )}
    </Box>
  );
}
