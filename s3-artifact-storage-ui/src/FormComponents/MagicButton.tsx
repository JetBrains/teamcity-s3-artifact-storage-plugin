import {React} from '@jetbrains/teamcity-api';
import Button from '@jetbrains/ring-ui/components/button/button';
import magicWandIcon from '@jetbrains/icons/magic-wand';

type OwnProps = {
  disabled?: boolean,
  title?: string,
  onClick: () => Promise<void>
}

export default function MagicButton({title, onClick, disabled}: OwnProps) {
  return (
    <Button
      title={title}
      icon={magicWandIcon}
      primary
      onClick={onClick}
      disabled={disabled}
    />
  );
}
