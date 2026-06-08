import { Box } from '@mui/material';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownContentProps {
  children: string;
}

export default function MarkdownContent({ children }: MarkdownContentProps) {
  return (
    <Box
      sx={{
        '& > :first-of-type': { mt: 0 },
        '& > :last-child': { mb: 0 },
        '& p': { my: 1.5 },
        '& ul, & ol': { my: 1.5, pl: 3 },
        '& li + li': { mt: 0.5 },
        '& h1': { typography: 'h4', mt: 2, mb: 1 },
        '& h2': { typography: 'h5', mt: 2, mb: 1 },
        '& h3': { typography: 'h6', mt: 2, mb: 1 },
        '& h4, & h5, & h6': { typography: 'subtitle1', mt: 2, mb: 1 },
        '& a': { color: 'primary.main' },
        '& code': {
          fontFamily: 'monospace',
          bgcolor: 'action.hover',
          borderRadius: 0.5,
          px: 0.5,
          py: 0.25,
        },
        '& pre': {
          bgcolor: 'action.hover',
          borderRadius: 1,
          overflowX: 'auto',
          p: 1.5,
        },
        '& pre code': {
          bgcolor: 'transparent',
          p: 0,
        },
        '& blockquote': {
          borderColor: 'divider',
          borderLeft: 4,
          borderStyle: 'solid',
          color: 'text.secondary',
          m: 0,
          pl: 2,
        },
        '& table': {
          borderCollapse: 'collapse',
          my: 2,
          width: '100%',
        },
        '& th, & td': {
          border: 1,
          borderColor: 'divider',
          p: 1,
          textAlign: 'left',
        },
      }}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </Box>
  );
}
